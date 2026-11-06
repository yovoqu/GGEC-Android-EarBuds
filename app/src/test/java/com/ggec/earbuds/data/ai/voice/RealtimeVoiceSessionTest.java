package com.ggec.earbuds.data.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeAudioCapture;
import com.ggec.earbuds.testing.ai.FakeAudioPlayback;
import com.ggec.earbuds.testing.ai.FakeCredentialStore;
import com.ggec.earbuds.testing.ai.FakeRealtimeLink;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.yovo.lib_ai.error.YoAiErrorKind;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 实时语音链路的演练：连上 → 开麦 → 上行重采样 → 下行文字/音频 → 打断 → 挂断。
 *
 * <p>执行器一律用 {@code Runnable::run}：整条链路退化成一段确定的顺序代码，
 * "第几个事件之后是哪个状态"才能写成确定值，而不是靠 sleep 去赌。</p>
 *
 * <p>默认双工是 {@link VoiceDuplex#HALF_DUPLEX}——真机没有回声消除时就是这个档位。
 * 它带来一条硬性语义：助手发声期间上行闸门是关的，{@code speech_started} 不可能是真用户，
 * 必须忽略。要测"打断"，必须显式用全双工。</p>
 */
public class RealtimeVoiceSessionTest {

    private static final String API_KEY = "sk-test-key-1234";
    private static final AiVoiceModel VOICE_MODEL = AiVoiceModel.QWEN;

    /** 16kHz/16bit 的 20ms 一帧。 */
    private static final int FRAME_BYTES = 640;

    private final ManualClock clock = new ManualClock();
    private final MemorySettingsRepository settings = new MemorySettingsRepository(
            AiFixtures.settings(null, AiFixtures.voice(VOICE_MODEL, API_KEY)));
    private final FakeAudioCapture capture = new FakeAudioCapture();
    private final FakeAudioPlayback playback = new FakeAudioPlayback();
    private final FakeRealtimeLink link = new FakeRealtimeLink();
    private final List<AiVoiceSnapshot> snapshots = new ArrayList<>();

    private RealtimeVoiceSession session;

    private void newSession(VoiceDuplex duplex) {
        session = new RealtimeVoiceSession(settings,
                new FakeCredentialStore().withVoiceKey(VOICE_MODEL, API_KEY),
                capture, playback, link, clock, Runnable::run, duplex);
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
    public void opensThenListensAndUpsamplesCaptureTo24k() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);

        assertEquals("start 之后先是连接中", AiVoiceSnapshot.State.CONNECTING, last().state());
        assertEquals(1, link.opens);
        assertNotNull(link.lastConfig);
        assertEquals(VOICE_MODEL.baseUrl(), link.lastConfig.baseUrl());
        assertEquals(API_KEY, link.lastConfig.apiKey());
        assertEquals(VOICE_MODEL.realtimeModel(), link.lastConfig.model());

        // 服务端会话建好，才开始采：否则握手失败时麦克风已经开着，用户白等。
        assertFalse("连上之前不该开麦", capture.started());
        link.opened();

        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        assertEquals("采集按 16k 打开", 16_000, capture.sampleRateHz);
        assertTrue("聆听时必须开上行", capture.uplinkEnabled);

        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0.2f);

        assertEquals(1, link.appended.size());
        assertEquals("16k→24k 之后字节数是 1.5 倍", FRAME_BYTES * 3 / 2, link.appended.get(0).length);
    }

    @Test
    public void assistantAudioStartsPlaybackAndEntersSpeaking() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();

        link.audio(FakeAudioCapture.pcm(480));

        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());
        assertEquals("下行按 24k 打开播放", 24_000, playback.sampleRateHz);
        assertEquals(1, playback.written.size());
        assertFalse("半双工：助手说话时必须关上行，否则麦克风会把它自己的声音送回去",
                capture.uplinkEnabled);

        link.audio(FakeAudioCapture.pcm(480));
        assertEquals("后续音频块直接写，不再重开通道", 2, playback.written.size());
        assertEquals(1, playback.starts);
    }

    @Test
    public void halfDuplexIgnoresSpeechStartedWhileAssistantIsSpeaking() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        link.audio(FakeAudioCapture.pcm(480));
        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());

        // 上行是关的，这条 speech_started 只可能是服务端对残余音频的误判（回声）。
        link.speechStarted();

        assertEquals("回声称不上插话，不许打断", AiVoiceSnapshot.State.SPEAKING, last().state());
        assertEquals(0, playback.flushes);
        assertEquals(0, link.cancels);
        assertEquals(0, link.clears);
    }

    @Test
    public void fullDuplexLetsUserBargeIn() {
        // 采集打开后才会根据 AEC 事实把双工升级为全双工；单测用假采集显式声明 AEC 生效。
        capture.echoCancellationActive = true;
        newSession(VoiceDuplex.FULL_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        link.audio(FakeAudioCapture.pcm(480));
        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());
        assertTrue("AEC 生效后升级全双工：播放期间上行保持开启", capture.uplinkEnabled);

        link.speechStarted();

        assertEquals("插话时先丢掉没播完的旧回答", 1, playback.flushes);
        assertEquals("同时让服务端停止生成", 1, link.cancels);
        assertEquals("丢掉还没断句的上行，避免半句被算进下一轮", 1, link.clears);
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
    }

    @Test
    public void interruptCancelsTheResponseAndReturnsToListening() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        link.audio(FakeAudioCapture.pcm(480));

        session.interrupt();

        assertEquals(1, link.cancels);
        assertEquals(1, playback.flushes);
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        assertTrue("打断后上行要重新打开", capture.uplinkEnabled);
    }

    @Test
    public void mutedStopsUplinkEvenWhileListening() {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();

        session.setMuted(true);

        assertEquals(AiVoiceSnapshot.State.MUTED, last().state());
        assertFalse(capture.uplinkEnabled);
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0.2f);
        assertEquals("静音时一帧都不该上行", 0, link.appended.size());

        session.setMuted(false);
        assertTrue(capture.uplinkEnabled);
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
        assertTrue("失败后要放开麦克风", capture.stops > 0);
        assertTrue("失败后要放开播放通道", playback.stops > 0);
        assertEquals("失败后要关掉长连接", 1, link.closes);
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
        assertTrue(capture.stops > 0);
        assertTrue(playback.stops > 0);
        assertEquals(1, link.closes);

        int ended = snapshots.size();
        session.stop();
        link.audio(FakeAudioCapture.pcm(480));
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
        assertFalse("思考时上行也应关闭（半双工）", capture.uplinkEnabled);

        link.responseDone();

        assertTrue("response.done 才定稿", last().turns().get(0).finalTranscript());
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        assertTrue("回答结束、没有音频要播，应立刻开麦", capture.uplinkEnabled);
    }

    @Test
    public void responseDoneWithAudioKeepsUplinkClosedUntilDrain() throws Exception {
        newSession(VoiceDuplex.HALF_DUPLEX);
        session.start(snapshots::add);
        link.opened();
        link.audio(FakeAudioCapture.pcm(24_000));   // 24k 单声道 16bit = 500ms
        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());

        link.responseDone();

        assertEquals("生成完但还没播完，对外仍是 SPEAKING", AiVoiceSnapshot.State.SPEAKING,
                last().state());
        assertFalse("正在播，不能开麦", capture.uplinkEnabled);

        clock.advance(700);
        // 真实调度器按墙钟投递 PlaybackDrained；这里等它到点（620ms）。
        Thread.sleep(900);

        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        assertTrue("播完了才开麦", capture.uplinkEnabled);
    }

    @Test
    public void withoutAKeyItFailsBeforeTouchingTheNetwork() {
        FakeRealtimeLink untouched = new FakeRealtimeLink();
        List<AiVoiceSnapshot> events = new ArrayList<>();
        MemorySettingsRepository noKey = new MemorySettingsRepository(AiSettings.EMPTY);

        new RealtimeVoiceSession(noKey, new FakeCredentialStore(),
                new FakeAudioCapture(), new FakeAudioPlayback(), untouched,
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
                new FakeAudioCapture(), new FakeAudioPlayback(), other,
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

    private AiVoiceSnapshot last() {
        return snapshots.get(snapshots.size() - 1);
    }
}
