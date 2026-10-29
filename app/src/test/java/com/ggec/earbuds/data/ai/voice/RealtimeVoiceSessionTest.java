package com.ggec.earbuds.data.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeAudioCapture;
import com.ggec.earbuds.testing.ai.FakeAudioPlayback;
import com.ggec.earbuds.testing.ai.FakeRealtimeLink;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.yovo.lib_ai.error.YoAiErrorKind;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 实时语音链路的演练：连上 → 开麦 → 上行重采样 → 下行文字/音频 → 打断 → 挂断。
 *
 * <p>执行器一律用 {@code Runnable::run}：整条链路退化成一段确定的顺序代码，
 * "第几个事件之后是哪个状态"才能写成确定值，而不是靠 sleep 去赌。</p>
 */
public class RealtimeVoiceSessionTest {

    private static final String PLATFORM_ID = "deepseek";
    private static final String API_KEY = "sk-test-key-1234";
    private static final String REALTIME_MODEL = "gpt-4o-realtime-preview";

    /** 16kHz/16bit 的 20ms 一帧。 */
    private static final int FRAME_BYTES = 640;

    private final ManualClock clock = new ManualClock();
    private final AiSelection selection = new AiSelection(PLATFORM_ID, REALTIME_MODEL);
    private final AiPlatformConfig platform = AiFixtures
            .platformOf(PLATFORM_ID, API_KEY, AiFixtures.realtimeModel(REALTIME_MODEL))
            .withCredentials(PLATFORM_ID, API_KEY, "https://api.example.com", AiProtocol.CHAT_COMPLETIONS);
    private final MemorySettingsRepository settings = new MemorySettingsRepository(
            new AiSettings(List.of(platform), AiInteractionMode.VOICE, null, selection,
                    AiChatPreferences.DEFAULT));
    private final FakeAudioCapture capture = new FakeAudioCapture();
    private final FakeAudioPlayback playback = new FakeAudioPlayback();
    private final FakeRealtimeLink link = new FakeRealtimeLink();
    private final List<AiVoiceSnapshot> snapshots = new ArrayList<>();

    private RealtimeVoiceSession session;

    @Before
    public void setUp() {
        session = new RealtimeVoiceSession(settings, capture, playback, link, clock, Runnable::run);
        session.start(selection, snapshots::add);
    }

    @Test
    public void opensThenListensAndUpsamplesCaptureTo24k() {
        assertEquals("start 之后先是连接中", AiVoiceSnapshot.State.CONNECTING, last().state());
        assertEquals(1, link.opens);
        assertNotNull(link.lastConfig);
        // 端口收的是平台主机基址；wss 与 /v1/realtime 的拼法留在 lib_ai，不在这里重复一次。
        assertEquals("https://api.example.com", link.lastConfig.baseUrl());
        assertEquals(API_KEY, link.lastConfig.apiKey());
        assertEquals(REALTIME_MODEL, link.lastConfig.model());
        assertEquals(16_000, link.lastConfig.inputSampleRateHz());
        assertEquals(24_000, link.lastConfig.outputSampleRateHz());

        // 服务端会话建好，才开始采：否则握手失败时麦克风已经开着，用户白等。
        assertFalse("连上之前不该开麦", capture.started());
        link.opened();

        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        assertEquals("采集按 16k 打开", 16_000, capture.sampleRateHz);

        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0.2f);

        assertEquals(1, link.appended.size());
        assertEquals("16k→24k 之后字节数是 1.5 倍", FRAME_BYTES * 3 / 2, link.appended.get(0).length);
    }

    @Test
    public void assistantAudioStartsPlaybackAndEntersSpeaking() {
        link.opened();

        link.audio(FakeAudioCapture.pcm(480));

        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());
        assertEquals("下行按 24k 打开播放", 24_000, playback.sampleRateHz);
        assertEquals(1, playback.written.size());

        link.audio(FakeAudioCapture.pcm(480));
        assertEquals("后续音频块直接写，不再重开通道", 2, playback.written.size());
        assertEquals(1, playback.starts);
    }

    @Test
    public void userSpeechFlushesPlaybackAndReturnsToListening() {
        link.opened();
        link.audio(FakeAudioCapture.pcm(480));
        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());

        link.speechStarted();

        assertEquals("插话时先丢掉没播完的旧回答", 1, playback.flushes);
        assertEquals("同时让服务端停止生成", 1, link.cancels);
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
    }

    @Test
    public void interruptCancelsTheResponseAndReturnsToListening() {
        link.opened();
        link.audio(FakeAudioCapture.pcm(480));

        session.interrupt();

        assertEquals(1, link.cancels);
        assertEquals(1, playback.flushes);
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
    }

    @Test
    public void failureIsReportedWithReasonAndReleasesDevices() {
        link.opened();

        link.fail(AiFailureKind.AUTHENTICATION, "Incorrect API key provided.");

        assertEquals(AiVoiceSnapshot.State.FAILED, last().state());
        assertNotNull(last().failure());
        assertEquals(AiFailureKind.AUTHENTICATION, last().failure().kind());
        assertEquals("Incorrect API key provided.", last().failure().getMessage());
        assertTrue("失败后要放开麦克风", capture.stops > 0);
        assertTrue("失败后要放开播放通道", playback.stops > 0);
        assertEquals("失败后要关掉长连接", 1, link.closes);
    }

    @Test
    public void stopPublishesEndedOnceAndThenGoesSilent() {
        link.opened();
        int before = snapshots.size();

        session.stop();

        assertEquals(AiVoiceSnapshot.State.ENDED, last().state());
        assertEquals("挂断只多出这一条快照", before + 1, snapshots.size());
        assertTrue(capture.stops > 0);
        assertTrue(playback.stops > 0);
        assertEquals(1, link.closes);

        int ended = snapshots.size();
        session.stop();                                     // 幂等
        link.audio(FakeAudioCapture.pcm(480));              // 迟到的下行音频
        link.text("迟到的增量");
        link.speechStarted();
        link.fail(AiFailureKind.NETWORK, "连接断了");

        assertEquals("挂断之后一条快照都不该再有", ended, snapshots.size());
        assertEquals("链路只关一次", 1, link.closes);
        assertEquals("迟到的音频不该再上行", 0, link.appended.size());
    }

    @Test
    public void assistantTextAccumulatesIntoOneTurnThatOnlyFinishesOnResponseDone() {
        link.opened();

        link.text("你");
        link.text("好");

        AiVoiceSnapshot.Turn turn = last().turns().get(0);
        assertEquals("文字是增量累积的", "你好", turn.text());
        assertFalse("没收到 response.done 之前不是定稿", turn.finalTranscript());
        assertEquals(AiVoiceSnapshot.State.THINKING, last().state());

        link.responseDone();

        assertTrue("response.done 才定稿", last().turns().get(0).finalTranscript());
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
    }

    @Test
    public void withoutAModelItFailsBeforeTouchingTheNetwork() {
        FakeRealtimeLink untouched = new FakeRealtimeLink();
        List<AiVoiceSnapshot> events = new ArrayList<>();

        new RealtimeVoiceSession(settings, new FakeAudioCapture(), new FakeAudioPlayback(), untouched,
                clock, Runnable::run)
                .start(new AiSelection(PLATFORM_ID, ""), events::add);

        AiVoiceSnapshot last = events.get(events.size() - 1);
        assertEquals(AiVoiceSnapshot.State.FAILED, last.state());
        assertNotNull(last.failure());
        assertEquals(AiFailureKind.NO_MODEL, last.failure().kind());
        assertEquals("缺模型就不该去连一条注定被拒的连接", 0, untouched.opens);
    }

    @Test
    public void aDeletedPlatformFailsWithNoPlatform() {
        FakeRealtimeLink untouched = new FakeRealtimeLink();
        List<AiVoiceSnapshot> events = new ArrayList<>();

        new RealtimeVoiceSession(settings, new FakeAudioCapture(), new FakeAudioPlayback(), untouched,
                clock, Runnable::run)
                .start(new AiSelection("gone", REALTIME_MODEL), events::add);

        AiVoiceSnapshot last = events.get(events.size() - 1);
        assertEquals(AiVoiceSnapshot.State.FAILED, last.state());
        assertEquals(AiFailureKind.NO_PLATFORM, last.failure().kind());
        assertEquals(0, untouched.opens);
    }

    /**
     * 适配器的错误分类翻译。放在这里是因为这段映射是 lib_ai 的分类与领域分类之间唯一的翻译点：
     * 翻错一格，界面就会把"密钥错了"显示成"服务端故障，请稍后重试"。
     */
    @Test
    public void adapterTranslatesLibAiErrorKinds() {
        assertEquals(AiFailureKind.AUTHENTICATION, RealtimeLinkAdapter.kindOf(YoAiErrorKind.AUTHENTICATION));
        assertEquals(AiFailureKind.PROTOCOL, RealtimeLinkAdapter.kindOf(YoAiErrorKind.PROTOCOL));
        assertEquals(AiFailureKind.NETWORK, RealtimeLinkAdapter.kindOf(YoAiErrorKind.NETWORK));
        assertEquals(AiFailureKind.UNKNOWN, RealtimeLinkAdapter.kindOf(null));
    }

    private AiVoiceSnapshot last() {
        return snapshots.get(snapshots.size() - 1);
    }
}
