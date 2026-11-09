package com.ggec.earbuds.data.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.testing.ai.FakeAudioSource;
import com.ggec.earbuds.testing.ai.FakeRealtimeLink;
import com.ggec.earbuds.testing.ai.ManualClock;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link AudioUplinkPipeline} 单元测试：验证门控零开销快路径、任意采样率重采样、
 * 电平节流派发、外部 PCM 推流源以及基于代数令牌的无缝热切换。
 */
public class AudioUplinkPipelineTest {

    private FakeRealtimeLink link;
    private ManualClock clock;
    private List<Float> levels;
    private List<AiFailureKind> failures;
    private AudioUplinkPipeline pipeline;

    @Before
    public void setUp() {
        link = new FakeRealtimeLink();
        clock = new ManualClock();
        levels = new ArrayList<>();
        failures = new ArrayList<>();
        pipeline = new AudioUplinkPipeline(
                link,
                clock,
                levels::add,
                (kind, detail) -> failures.add(kind));
    }

    @Test
    public void disabledGateSkipsResamplingLevelMeterAndLinkSend() {
        FakeAudioSource source = new FakeAudioSource(
                "mic:1", AiAudioSource.Kind.BUILTIN_MIC, "Built-in Mic", 16_000);

        pipeline.start(source, 24_000);
        // start 后默认未开启上行闸门
        source.emit(FakeAudioSource.pcm(640), 16_000);

        assertTrue("未开启闸门时不得写网", link.appended.isEmpty());
        assertTrue("未开启闸门时不得派发电平事件", levels.isEmpty());

        pipeline.setEnabled(true);
        source.emit(FakeAudioSource.pcm(640), 16_000);
        assertEquals(1, link.appended.size());
        assertEquals(1, levels.size());

        pipeline.setEnabled(false);
        source.emit(FakeAudioSource.pcm(640), 16_000);
        assertEquals("再次关闭闸门后必须立刻阻断上行", 1, link.appended.size());
        assertEquals(1, levels.size());
    }

    @Test
    public void resamplesMultipleRatesToNegotiatedWireRate() {
        FakeAudioSource source = new FakeAudioSource(
                "usb:1", AiAudioSource.Kind.USB_AUDIO, "USB Mic", 48_000);

        // 1. 48kHz -> 24kHz (0.5x)
        pipeline.start(source, 24_000);
        pipeline.setEnabled(true);
        source.emit(FakeAudioSource.pcm(1920), 48_000);
        assertEquals(1, link.appended.size());
        assertEquals(960, link.appended.get(0).length);
        assertEquals(Integer.valueOf(24_000), link.appendedRates.get(0));

        // 2. 16kHz -> 24kHz (1.5x)
        source.emit(FakeAudioSource.pcm(640), 16_000);
        assertEquals(2, link.appended.size());
        assertEquals(960, link.appended.get(1).length);
        assertEquals(Integer.valueOf(24_000), link.appendedRates.get(1));
        pipeline.stop();

        // 3. 48kHz -> 16kHz (1/3x)
        pipeline.start(source, 16_000);
        pipeline.setEnabled(true);
        source.emit(FakeAudioSource.pcm(1920), 48_000);
        assertEquals(3, link.appended.size());
        assertEquals(640, link.appended.get(2).length);
        assertEquals(Integer.valueOf(16_000), link.appendedRates.get(2));
    }

    @Test
    public void throttlesLevelsByIntervalAndDelta() {
        FakeAudioSource source = new FakeAudioSource(
                "mic:1", AiAudioSource.Kind.BUILTIN_MIC, "Built-in Mic", 16_000);
        pipeline.start(source, 16_000);
        pipeline.setEnabled(true);

        // 首帧立即发送（now=1000, lastLevelAt=0 >= 45ms）
        source.emit(FakeAudioSource.pcmTone(640, (short) 400), 16_000);
        assertEquals(1, levels.size());

        // 10ms 后同幅值：被节流跳过
        clock.advance(10L);
        source.emit(FakeAudioSource.pcmTone(640, (short) 400), 16_000);
        assertEquals(1, levels.size());

        // 40ms 后（累计 50ms >= 45ms）：按周期放出
        clock.advance(40L);
        source.emit(FakeAudioSource.pcmTone(640, (short) 400), 16_000);
        assertEquals(2, levels.size());

        // 仅隔 5ms 但幅值大幅跃升（delta >= 0.02）：立刻放出
        clock.advance(5L);
        source.emit(FakeAudioSource.pcmTone(640, (short) 16_000), 16_000);
        assertEquals(3, levels.size());
    }

    @Test
    public void switchSourceHotSwapsAndDropsStaleFramesByGenerationToken() {
        FakeAudioSource oldSource = new FakeAudioSource(
                "mic:old", AiAudioSource.Kind.BUILTIN_MIC, "Old Mic", 16_000);
        FakeAudioSource newSource = new FakeAudioSource(
                "bt:new", AiAudioSource.Kind.BLUETOOTH_BLE, "New BLE Mic", 48_000);

        pipeline.start(oldSource, 24_000);
        pipeline.setEnabled(true);
        AiAudioSource.Listener capturedOldListener = oldSource.listener;
        assertTrue(oldSource.started());

        oldSource.emit(FakeAudioSource.pcm(640), 16_000);
        assertEquals(1, link.appended.size());

        // 热切到新源
        pipeline.switchSource(newSource);

        assertFalse("旧源必须已停止", oldSource.started());
        assertEquals(1, oldSource.stops);
        assertTrue("新源必须已启动", newSource.started());

        // 模拟旧线程在 stop 后才投递的迟到帧和迟到故障：按代数直接 O(1) 丢弃
        capturedOldListener.onPcm(FakeAudioSource.pcm(640), 16_000);
        capturedOldListener.onFailure(AiFailureKind.UNKNOWN, "stale device error");
        assertEquals("旧源残余帧不得进入链路", 1, link.appended.size());
        assertTrue("旧源残余故障不得上报", failures.isEmpty());

        // 新源按 48kHz 推入并自动重采样到协商线速率 24kHz
        newSource.emit(FakeAudioSource.pcm(1920), 48_000);
        assertEquals(2, link.appended.size());
        assertEquals(960, link.appended.get(1).length);
    }

    @Test
    public void externalPcmSourceStreamsThroughPipelineAndStopsCleanly() {
        ExternalPcmAudioSource external = new ExternalPcmAudioSource(
                "ext:1", "Private BLE Channel", 16_000);
        FakeAudioSource mic = new FakeAudioSource(
                "mic:1", AiAudioSource.Kind.BUILTIN_MIC, "Phone Mic", 16_000);

        pipeline.start(mic, 16_000);
        pipeline.setEnabled(true);

        // 未绑定到管线前推流应被静默忽略
        external.pushPcm(FakeAudioSource.pcm(640));
        assertTrue(link.appended.isEmpty());

        pipeline.switchSource(external);
        assertEquals(1, mic.stops);

        external.pushPcm(FakeAudioSource.pcm(640));
        external.pushPcm(FakeAudioSource.pcm(1920), 48_000);
        assertEquals(2, link.appended.size());
        assertEquals(640, link.appended.get(0).length);
        assertEquals(640, link.appended.get(1).length);

        external.fail(AiFailureKind.NETWORK, "gatt link lost");
        assertEquals(1, failures.size());
        assertSame(AiFailureKind.NETWORK, failures.get(0));

        pipeline.stop();
        pipeline.stop(); // 幂等
        external.pushPcm(FakeAudioSource.pcm(640));
        assertEquals("停止后推流不得再生效", 2, link.appended.size());
    }
}
