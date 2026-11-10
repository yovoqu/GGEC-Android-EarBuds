package com.ggec.earbuds.data.ai.voice;

import com.ggec.earbuds.provider.lib_ai.RealtimeLinkAdapter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeAudioPlayback;
import com.ggec.earbuds.testing.ai.FakeAudioSource;
import com.ggec.earbuds.testing.ai.FakeAudioSourceCatalog;
import com.ggec.earbuds.testing.ai.FakeCredentialStore;
import com.ggec.earbuds.testing.ai.FakeRealtimeLink;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.yovo.lib_ai.error.YoAiErrorKind;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 实时语音全链路集成单测：连上 → 开麦/重采样或直通 → 源热切换/断线回退 → 下行文字/音频 → 打断(Truncate) → 挂断。
 *
 * <p>执行器采用 {@code Runnable::run}，所有事件转移确定可执行。</p>
 */
public class RealtimeVoiceSessionTest {

    private static final String API_KEY = "sk-test-key-1234";
    private static final AiVoiceModel VOICE_MODEL = AiVoiceModel.QWEN;

    /** 16kHz/16bit 的 20ms 一帧。 */
    private static final int FRAME_BYTES = 640;

    private final ManualClock clock = new ManualClock();
    private final MemorySettingsRepository settings = new MemorySettingsRepository(
            AiFixtures.settings(null, AiFixtures.voice(VOICE_MODEL, API_KEY)));
    private final FakeAudioSource builtinMic = new FakeAudioSource(
            "builtin:mic", AiAudioSource.Kind.BUILTIN_MIC, "手机麦克风", 16_000);
    private final FakeAudioSourceCatalog catalog = new FakeAudioSourceCatalog();
    private final FakeAudioPlayback playback = new FakeAudioPlayback();
    private final FakeRealtimeLink link = new FakeRealtimeLink();
    private final List<AiVoiceSnapshot> snapshots = new ArrayList<>();

    private RealtimeVoiceSession session;

    @Before
    public void setUp() {
        catalog.withSource(builtinMic);
    }

    private void newSession(VoiceDuplex duplex) {
        session = new RealtimeVoiceSession(settings,
                new FakeCredentialStore().withVoiceKey(VOICE_MODEL, API_KEY),
                catalog, playback, link, clock, Runnable::run, duplex);
    }

    @Test
    public void userTranscriptFlowsIntoSnapshot() {
        newSession(VoiceDuplex.FULL_DUPLEX);
        session.start(snapshots::add);
        link.opened();

        link.userTranscript("明天的天气如何", "user-123");
        assertEquals(1, last().turns().size());
        assertTrue(last().turns().get(0).user());
        assertEquals("明天的天气如何", last().turns().get(0).text());

        link.text("明天晴转多云");
        assertEquals(2, last().turns().size());
        assertFalse(last().turns().get(1).user());
        assertEquals("明天晴转多云", last().turns().get(1).text());
    }

    @Test
    public void opensThenListensAndUpsamples16kCaptureTo24kWireRate() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);

        assertEquals("start 之后先是连接中", AiVoiceSnapshot.State.CONNECTING, last().state());
        assertEquals(1, link.opens);
        assertNotNull(link.lastConfig);
        assertEquals(VOICE_MODEL.baseUrl(), link.lastConfig.baseUrl());
        assertEquals(API_KEY, link.lastConfig.apiKey());
        assertEquals(VOICE_MODEL.realtimeModel(), link.lastConfig.model());
        assertEquals(1, catalog.observerCount());

        // 服务端会话建好，才开始采：否则握手失败时麦克风已经开着。
        assertFalse("连上之前不该开麦", builtinMic.started());
        link.opened(24_000, 24_000);

        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        assertTrue("连上后才启动默认输入源", builtinMic.started());
        assertEquals(builtinMic.descriptor(), last().activeSource());

        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));

        assertEquals(1, link.appended.size());
        assertEquals("16k→24k 之后字节数是 1.5 倍", FRAME_BYTES * 3 / 2, link.appended.get(0).length);
        assertEquals(Integer.valueOf(24_000), link.appendedRates.get(0));
    }

    @Test
    public void negotiated16kWireRateBypassesResamplingZeroCopy() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened(16_000, 24_000);

        byte[] frame = FakeAudioSource.pcm(FRAME_BYTES);
        builtinMic.emit(frame, 16_000);

        assertEquals(1, link.appended.size());
        assertEquals("16k 源直通 16k 线速率无缩放", FRAME_BYTES, link.appended.get(0).length);
        assertEquals(Integer.valueOf(16_000), link.appendedRates.get(0));
    }

    @Test
    public void assistantAudioStartsPlaybackAndEntersSpeaking() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();

        link.audio(FakeAudioSource.pcm(480));

        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());
        assertEquals("下行按 24k 打开播放", 24_000, playback.sampleRateHz);
        assertEquals(1, playback.written.size());

        // 半双工：助手说话时上行闸门闭合
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals("半双工：助手说话时不得推流上行", 0, link.appended.size());

        link.audio(FakeAudioSource.pcm(480));
        assertEquals("后续同采样率音频块直接写，不再重开通道", 2, playback.written.size());
        assertEquals(1, playback.starts);
    }

    @Test
    public void halfDuplexIgnoresSpeechStartedWhileAssistantIsSpeaking() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        link.audio(FakeAudioSource.pcm(480));
        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());

        // 上行是关的，这条 speech_started 只可能是服务端对残余音频的误判（回声）。
        link.speechStarted();

        assertEquals("回声称不上插话，不许打断", AiVoiceSnapshot.State.SPEAKING, last().state());
        assertEquals(0, playback.flushes);
        assertEquals(0, link.cancels);
        assertEquals(0, link.clears);
        assertEquals(0, link.truncates);
    }

    @Test
    public void fullDuplexLetsUserBargeInAndSendsTruncate() {
        newSession(VoiceDuplex.FULL_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        link.audio(FakeAudioSource.pcm(480), "item-ai-42");
        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());

        // 全双工播放期间上行保持开启
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals(1, link.appended.size());

        link.speechStarted();

        assertEquals("插话时先丢掉没播完的旧回答", 1, playback.flushes);
        assertEquals("同时让服务端停止生成", 1, link.cancels);
        assertEquals("丢掉还没断句的上行，避免半句被算进下一轮", 1, link.clears);
        assertEquals("通知服务端截断到实际已播放进度", 1, link.truncates);
        assertEquals("item-ai-42", link.truncatedItemId);
        assertEquals(10, link.truncatedAudioEndMs);
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
    }

    @Test
    public void interruptCancelsTheResponseTruncatesAndReturnsToListening() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        link.audio(FakeAudioSource.pcm(480), "item-ai-99");

        session.interrupt();

        assertEquals(1, link.cancels);
        assertEquals(1, link.truncates);
        assertEquals("item-ai-99", link.truncatedItemId);
        assertEquals(1, playback.flushes);
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());

        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals("打断后上行要重新打开", 1, link.appended.size());
    }

    @Test
    public void mutedStopsUplinkEvenWhileListening() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();

        session.setMicMuted(true);

        assertEquals("静音麦克风绝不影响AI会话的LISTENING状态", AiVoiceSnapshot.State.LISTENING, last().state());
        assertTrue("快照反映真实的micMuted状态", last().micMuted());
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals("静音时一帧都不该上行", 0, link.appended.size());

        session.setMicMuted(false);
        assertFalse(last().micMuted());
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals(1, link.appended.size());
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
    }

    @Test
    public void preStartMuteAndSourceSelectionSurviveOpened() {
        FakeAudioSource usbMic = new FakeAudioSource(
                "usb:1", AiAudioSource.Kind.USB_AUDIO, "USB 麦克风", 48_000);
        catalog.withSource(usbMic);

        newSession(VoiceDuplex.HALF_DUPLEX);
        session.selectAudioSource("usb:1");
        session.setMicMuted(true);
        session.start(snapshots::add);
        link.opened(16_000, 24_000);

        assertTrue(last().micMuted());
        assertEquals(usbMic.descriptor(), last().activeSource());
        assertTrue(usbMic.started());
        assertFalse(builtinMic.started());

        usbMic.emit(FakeAudioSource.pcm(1920), 48_000);
        assertEquals("预置静音必须在连接成功后依然拦截上行", 0, link.appended.size());

        session.setMicMuted(false);
        usbMic.emit(FakeAudioSource.pcm(1920), 48_000);
        assertEquals(1, link.appended.size());
        assertEquals(FRAME_BYTES, link.appended.get(0).length);
    }

    @Test
    public void midCallSelectAudioSourceHotSwapsWithoutReopeningLink() {
        FakeAudioSource usbMic = new FakeAudioSource(
                "usb:1", AiAudioSource.Kind.USB_AUDIO, "USB 麦克风", 48_000);
        catalog.withSource(usbMic);

        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened(24_000, 24_000);
        assertEquals(builtinMic.descriptor(), last().activeSource());

        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals(1, link.appended.size());

        // 通话中热切换到 48k USB 麦
        session.selectAudioSource("usb:1");

        assertEquals("切源绝不重连 WebSocket 链路", 1, link.opens);
        assertEquals(0, link.closes);
        assertFalse("旧麦必须立即停采", builtinMic.started());
        assertEquals(1, builtinMic.stops);
        assertTrue("新麦必须立即启动", usbMic.started());
        assertEquals(usbMic.descriptor(), last().activeSource());

        // 48k -> 24k 自动重采样上行
        usbMic.emit(FakeAudioSource.pcm(1920), 48_000);
        assertEquals(2, link.appended.size());
        assertEquals(FRAME_BYTES * 3 / 2, link.appended.get(1).length);
    }

    @Test
    public void setAudioSourceSupportsExternalPcmPushMidCall() {
        ExternalPcmAudioSource external = new ExternalPcmAudioSource(
                "ble-gatt:1", "耳机自定义 PCM 通道", 16_000);
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened(24_000, 24_000);

        session.setAudioSource(external);
        assertEquals(external.descriptor(), last().activeSource());
        assertFalse(builtinMic.started());

        external.pushPcm(FakeAudioSource.pcm(FRAME_BYTES));
        external.pushPcm(FakeAudioSource.pcm(1920), 48_000);
        assertEquals(2, link.appended.size());
        assertEquals(FRAME_BYTES * 3 / 2, link.appended.get(0).length);
        assertEquals(FRAME_BYTES * 3 / 2, link.appended.get(1).length);
    }

    @Test
    public void removingActiveCatalogSourceAutoFallsBackToDefaultSource() {
        FakeAudioSource btSco = new FakeAudioSource(
                "bt:sco:1", AiAudioSource.Kind.BLUETOOTH_SCO, "Earbuds SCO", 16_000);
        catalog.withSource(btSco);

        newSession(VoiceDuplex.HALF_DUPLEX);
        session.selectAudioSource("bt:sco:1");
        session.start(snapshots::add);
        link.opened(16_000, 24_000);
        assertEquals(btSco.descriptor(), last().activeSource());
        assertTrue(btSco.started());

        // 模拟蓝牙耳机断连：目录移除 btSco
        catalog.removeSource("bt:sco:1");

        assertFalse(btSco.started());
        assertEquals(1, btSco.stops);
        assertTrue("自动回退到手机内置麦续传", builtinMic.started());
        assertEquals(builtinMic.descriptor(), last().activeSource());
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());

        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals(1, link.appended.size());
    }

    @Test
    public void failureIsReportedWithReasonAndReleasesDevices() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();

        link.fail(AiFailureKind.AUTHENTICATION, "Incorrect API key provided.");

        assertEquals(AiVoiceSnapshot.State.FAILED, last().state());
        assertNotNull(last().failure());
        assertEquals(AiFailureKind.AUTHENTICATION, last().failure().kind());
        assertEquals("Incorrect API key provided.", last().failure().getMessage());
        assertTrue("失败后要停用输入源", builtinMic.stops > 0);
        assertTrue("失败后要放开播放通道", playback.stops > 0);
        assertEquals("失败后要关掉长连接", 1, link.closes);
        assertEquals("失败后要注销目录观察者", 0, catalog.observerCount());
    }

    @Test
    public void stopPublishesEndedOnceAndThenGoesSilent() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        int before = snapshots.size();

        session.stop();

        assertEquals(AiVoiceSnapshot.State.ENDED, last().state());
        assertEquals("挂断只多出这一条快照", before + 1, snapshots.size());
        assertTrue(builtinMic.stops > 0);
        assertTrue(playback.stops > 0);
        assertEquals(1, link.closes);
        assertEquals(0, catalog.observerCount());

        int ended = snapshots.size();
        session.stop();
        link.audio(FakeAudioSource.pcm(480));
        link.text("迟到的增量");
        link.speechStarted();
        link.fail(AiFailureKind.NETWORK, "连接断了");

        assertEquals("挂断之后一条快照都不该再有", ended, snapshots.size());
        assertEquals("链路只关一次", 1, link.closes);
        assertEquals("迟到的音频不该再上行", 0, link.appended.size());
    }

    @Test
    public void assistantTextAccumulatesIntoOneTurnThatOnlyFinishesOnResponseDone() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();

        link.text("你");
        link.text("好");

        AiVoiceSnapshot.Turn turn = last().turns().get(0);
        assertEquals("文字是增量累积的", "你好", turn.text());
        assertFalse("没收到 response.done 之前不是定稿", turn.finalTranscript());
        assertEquals(AiVoiceSnapshot.State.THINKING, last().state());

        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals("思考时上行也应关闭（半双工）", 0, link.appended.size());

        link.responseDone();

        assertTrue("response.done 才定稿", last().turns().get(0).finalTranscript());
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals("回答结束、没有音频要播，应立刻开麦", 1, link.appended.size());
    }

    @Test
    public void responseDoneWithAudioKeepsUplinkClosedUntilDrain() throws Exception {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        link.audio(FakeAudioSource.pcm(24_000));   // 24k 单声道 16bit = 500ms
        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());

        link.responseDone();

        assertEquals("生成完但还没播完，对外仍是 SPEAKING", AiVoiceSnapshot.State.SPEAKING,
                last().state());
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals("正在播，不能开麦", 0, link.appended.size());

        clock.advance(700);
        // 真实调度器按墙钟投递 PlaybackDrained；这里等它到点（620ms）。
        Thread.sleep(900);

        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals("播完了才开麦", 1, link.appended.size());
    }

    @Test
    public void withoutAKeyItFailsBeforeTouchingTheNetwork() {
        FakeRealtimeLink untouched = new FakeRealtimeLink();
        List<AiVoiceSnapshot> events = new ArrayList<>();
        MemorySettingsRepository noKey = new MemorySettingsRepository(AiSettings.EMPTY);

        new RealtimeVoiceSession(noKey, new FakeCredentialStore(),
                catalog, new FakeAudioPlayback(), untouched,
                clock, Runnable::run, VoiceDuplex.HALF_DUPLEX)
                .start(events::add);

        AiVoiceSnapshot last = events.get(events.size() - 1);
        assertEquals(AiVoiceSnapshot.State.FAILED, last.state());
        assertNotNull(last.failure());
        assertEquals(AiFailureKind.VOICE_NOT_CONFIGURED, last.failure().kind());
        assertEquals("缺 Key 就不该去连一条注定被拒的连接", 0, untouched.opens);
    }

    @Test
    public void switchingThePresetChangesTheConnectionTarget() {
        FakeRealtimeLink other = new FakeRealtimeLink();
        List<AiVoiceSnapshot> events = new ArrayList<>();
        MemorySettingsRepository gpt = new MemorySettingsRepository(
                AiFixtures.settings(null, AiFixtures.voice(AiVoiceModel.CHATGPT, "sk-gpt")));

        new RealtimeVoiceSession(gpt, new FakeCredentialStore()
                .withVoiceKey(AiVoiceModel.CHATGPT, "sk-gpt"),
                catalog, new FakeAudioPlayback(), other,
                clock, Runnable::run, VoiceDuplex.HALF_DUPLEX)
                .start(events::add);

        assertEquals(AiVoiceModel.CHATGPT.baseUrl(), other.lastConfig.baseUrl());
        assertEquals("sk-gpt", other.lastConfig.apiKey());
        assertEquals(AiVoiceModel.CHATGPT.realtimeModel(), other.lastConfig.model());
    }

    @Test
    public void adapterTranslatesLibAiErrorKinds() {
        assertEquals(AiFailureKind.AUTHENTICATION, RealtimeLinkAdapter.kindOf(YoAiErrorKind.AUTHENTICATION));
        assertEquals(AiFailureKind.PROTOCOL, RealtimeLinkAdapter.kindOf(YoAiErrorKind.PROTOCOL));
        assertEquals(AiFailureKind.NETWORK, RealtimeLinkAdapter.kindOf(YoAiErrorKind.NETWORK));
        assertEquals(AiFailureKind.UNKNOWN, RealtimeLinkAdapter.kindOf(null));
    }

    @Test
    public void setDuplexSwitchesDuplexModeInSnapshot() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        assertEquals(VoiceDuplex.HALF_DUPLEX, last().duplex());

        session.setDuplex(VoiceDuplex.FULL_DUPLEX);
        assertEquals(VoiceDuplex.FULL_DUPLEX, last().duplex());

        session.setDuplex(VoiceDuplex.HALF_DUPLEX);
        assertEquals(VoiceDuplex.HALF_DUPLEX, last().duplex());
    }

    @Test
    public void sessionReadsConfiguredDefaultDuplexWhenNotPinned() {
        MemorySettingsRepository fullDefaultRepo = new MemorySettingsRepository(
                AiFixtures.settings(null,
                        AiFixtures.voice(VOICE_MODEL, API_KEY)
                                .withDefaultDuplex(VoiceDuplex.FULL_DUPLEX)));
        session = new RealtimeVoiceSession(fullDefaultRepo,
                new FakeCredentialStore().withVoiceKey(VOICE_MODEL, API_KEY),
                catalog, playback, link, clock, Runnable::run);

        session.start(snapshots::add);
        assertEquals("开场连接阶段即采用设置页的默认模式", VoiceDuplex.FULL_DUPLEX, last().duplex());

        link.opened();
        assertEquals("Opened 不得把默认模式冲回旧值", VoiceDuplex.FULL_DUPLEX, last().duplex());
        link.audio(FakeAudioSource.pcm(480));
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals(1, link.appended.size());

        link.speechStarted();
        assertEquals(1, playback.flushes);
        assertEquals(1, link.cancels);
    }

    @Test
    public void preOpenedSetDuplexIsPreservedAcrossOpened() {
        session = new RealtimeVoiceSession(settings,
                new FakeCredentialStore().withVoiceKey(VOICE_MODEL, API_KEY),
                catalog, playback, link, clock, Runnable::run);

        session.start(snapshots::add);
        assertEquals(VoiceDuplex.HALF_DUPLEX, last().duplex());

        session.setDuplex(VoiceDuplex.FULL_DUPLEX);
        assertEquals(VoiceDuplex.FULL_DUPLEX, last().duplex());

        link.opened();
        assertEquals("连线打开不得覆盖用户在 CONNECTING 期间切好的模式",
                VoiceDuplex.FULL_DUPLEX, last().duplex());
        link.audio(FakeAudioSource.pcm(480));
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals(1, link.appended.size());
    }

    @Test
    public void midCallDuplexSwitchingUpdatesBothUplinkAndSpeechInterruption() {
        newSession(VoiceDuplex.FULL_DUPLEX);
        session.start(snapshots::add);
        link.opened();

        // 1. 全双工切到半双工：立即闭麦上行并忽略后续回声 SpeechStarted
        session.setDuplex(VoiceDuplex.HALF_DUPLEX);
        link.audio(FakeAudioSource.pcm(480));
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals(0, link.appended.size());

        link.speechStarted();
        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());
        assertEquals(0, playback.flushes);
        assertEquals(0, link.cancels);

        // 2. 半双工再切回全双工：同一轮回答中立即重新开闸，并且 SpeechStarted 生效
        session.setDuplex(VoiceDuplex.FULL_DUPLEX);
        builtinMic.emit(FakeAudioSource.pcm(FRAME_BYTES));
        assertEquals(1, link.appended.size());

        link.speechStarted();
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        assertEquals(1, playback.flushes);
        assertEquals(1, link.cancels);
    }

    private AiVoiceSnapshot last() {
        return snapshots.get(snapshots.size() - 1);
    }
}
