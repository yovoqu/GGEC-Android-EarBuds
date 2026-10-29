package com.ggec.earbuds.data.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiUsage;
import com.ggec.earbuds.domain.ai.model.AiVoiceSettings;
import com.ggec.earbuds.domain.ai.port.AiChatResult;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeAudioCapture;
import com.ggec.earbuds.testing.ai.FakeAudioPlayback;
import com.ggec.earbuds.testing.ai.FakeSpeechRecognizer;
import com.ggec.earbuds.testing.ai.FakeSpeechSynthesizer;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemoryConversationRepository;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.ScriptedChatGateway;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 级联链路的端到端演练：采集 → 静音切句 → 转写 → 对话 → 朗读 → 回聆听。
 *
 * <p>执行器一律用 {@code Runnable::run}，时间用 {@link ManualClock}：
 * 这样整条链路变成一段确定的顺序代码，"第 900 毫秒才切句""打断之后不再有快照"
 * 这些断言才写得成确定值，而不是靠 sleep 去赌。</p>
 */
public class CascadedVoiceStrategyTest {
    @Test
    public void synthesis404SaysSynthesisIsMissingNotTranscription() {
        AiFailure mapped = CascadedVoiceStrategy.stepFailure(
                new AiFailure(AiFailureKind.NOT_FOUND, "没有找到该模型或接口"),
                "这个平台没有提供语音合成接口。换一个支持合成的平台，或在设置里改用全双工实时对话。");
        assertEquals(AiFailureKind.NOT_FOUND, mapped.kind());
        assertTrue(mapped.getMessage().contains("语音合成"));
    }

    @Test
    public void transcription404TellsTheUserToChangePlatformNotTheModelName() {
        AiFailure mapped = CascadedVoiceStrategy.transcriptionFailure(
                new AiFailure(AiFailureKind.NOT_FOUND, "没有找到该模型或接口"));
        assertEquals(AiFailureKind.NOT_FOUND, mapped.kind());
        assertTrue(mapped.getMessage().contains("语音转写"));
        assertTrue(mapped.getMessage().contains("全双工"));
    }

    @Test
    public void otherTranscriptionFailuresKeepThePlatformText() {
        AiFailure original = new AiFailure(AiFailureKind.RATE_LIMITED, "请求太频繁");
        assertEquals(original, CascadedVoiceStrategy.transcriptionFailure(original));
    }


    private static final int FRAME_BYTES = 640;   // 16kHz/16bit 的 20ms 一帧

    private final ManualClock clock = new ManualClock();
    private final MemorySettingsRepository settings = new MemorySettingsRepository(AiFixtures.readySettings());
    private final MemoryConversationRepository conversations = new MemoryConversationRepository();
    private final ScriptedChatGateway gateway = new ScriptedChatGateway();
    private final FakeAudioCapture capture = new FakeAudioCapture();
    private final FakeAudioPlayback playback = new FakeAudioPlayback();
    private final FakeSpeechRecognizer recognizer = new FakeSpeechRecognizer();
    private final FakeSpeechSynthesizer synthesizer = new FakeSpeechSynthesizer();
    private final List<AiVoiceSnapshot> snapshots = new ArrayList<>();

    private AiChatSession chat;
    private CascadedVoiceStrategy strategy;

    @Before
    public void setUp() {
        chat = new AiChatSession(settings, conversations, gateway, clock, clock, Runnable::run);
        strategy = new CascadedVoiceStrategy(settings, chat, capture, playback, recognizer, synthesizer,
                clock, Runnable::run, Runnable::run);
        strategy.start(AiVoiceSettings.defaults(), "deepseek", snapshots::add);
    }

    @Test
    public void utteranceIsCutBySilenceThenTranscribedAnsweredAndSpoken() {
        recognizer.result = "  你好  ";
        say();

        assertEquals("转写只该发生一次", 1, recognizer.calls.size());
        assertEquals(16_000, recognizer.sampleRateHz);
        assertTrue("送出去的是攒下来的那一句，不是空数组", recognizer.calls.get(0).length > 0);
        assertEquals("转写完成后台上是思考",
                AiVoiceSnapshot.State.THINKING, last().state());

        // 转写文本原样交给同一条对话会话：历史里能看到，上下文也是同一条。
        assertEquals(1, gateway.requests.size());
        List<AiMessage> messages = chat.state().conversation().messages();
        assertEquals("你好", messages.get(0).text());
        assertEquals("通话里的这句话必须进历史",
                "你好", conversations.stored.get(chat.state().conversation().id()).messages().get(0).text());

        gateway.lastListener().onText("你好呀");
        gateway.lastListener().onComplete(
                new AiChatResult("你好呀", null, AiFinishReason.STOP, AiUsage.ofTokens(3, 5)));

        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());
        assertEquals(List.of("你好呀"), synthesizer.spoken);

        synthesizer.finish();
        assertEquals("念完回到聆听", AiVoiceSnapshot.State.LISTENING, last().state());

        List<AiVoiceSnapshot.Turn> turns = last().turns();
        assertEquals(2, turns.size());
        assertTrue(turns.get(0).user());
        assertEquals("你好", turns.get(0).text());
        assertTrue("转写完成后用户这句话就是定稿", turns.get(0).finalTranscript());
        assertEquals("你好呀", turns.get(1).text());
        assertTrue(turns.get(1).finalTranscript());
    }

    @Test
    public void emptyTranscriptDoesNotReachTheChatSession() {
        recognizer.result = "   ";
        say();

        assertEquals(1, recognizer.calls.size());
        assertTrue("没听清就不该发消息", gateway.requests.isEmpty());
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
        assertTrue("占位的那条用户发言要撤掉", last().turns().isEmpty());
        assertNull(last().failure());
    }

    @Test
    public void shortPausesInsideASentenceDoNotSplitTheUtterance() {
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0.7f);
        clock.advance(400);
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0f);
        assertTrue("400ms 的停顿只是换气", recognizer.calls.isEmpty());

        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0.7f);
        clock.advance(500);
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0f);
        assertTrue("500ms 仍然不到 900ms 的阈值", recognizer.calls.isEmpty());

        clock.advance(500);
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0f);
        assertEquals(1, recognizer.calls.size());
    }

    @Test
    public void interruptStopsSynthesisFlushesPlaybackAndReturnsToListening() {
        recognizer.result = "讲个故事";
        say();
        gateway.lastListener().onComplete(
                new AiChatResult("从前有座山", null, AiFinishReason.STOP, AiUsage.ofTokens(1, 1)));
        assertEquals(AiVoiceSnapshot.State.SPEAKING, last().state());

        int stopsBefore = synthesizer.stops;
        int flushesBefore = playback.flushes;

        strategy.interrupt();

        assertEquals("合成要立刻停", stopsBefore + 1, synthesizer.stops);
        assertEquals("还没播完的旧回答要丢掉", flushesBefore + 1, playback.flushes);
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
    }

    @Test
    public void microphoneFailureBecomesAFailedSnapshot() {
        capture.fail(AiFailureKind.PERMISSION, "没有麦克风权限");

        AiVoiceSnapshot snapshot = last();
        assertEquals(AiVoiceSnapshot.State.FAILED, snapshot.state());
        assertNotNull("失败原因必须带上，不能静默", snapshot.failure());
        assertEquals(AiFailureKind.PERMISSION, snapshot.failure().kind());
        assertEquals("没有麦克风权限", snapshot.failure().getMessage());
        assertEquals("失败后要释放设备", 1, capture.stops);
    }

    @Test
    public void muteKeepsTheLevelButStopsUpstream() {
        strategy.setMuted(true);
        assertEquals(AiVoiceSnapshot.State.MUTED, last().state());
        assertTrue(capture.muted);

        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0.8f);
        assertEquals("静音期间光晕仍然要有输入电平", 0.8f, last().inputLevel(), 0.02f);
        assertTrue("静音期间攒下来的声音不能进转写", last().turns().isEmpty());

        clock.advance(5_000);
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0f);
        assertTrue(recognizer.calls.isEmpty());

        strategy.setMuted(false);
        assertEquals(AiVoiceSnapshot.State.LISTENING, last().state());
    }

    @Test
    public void utteranceThatCannotReachTheBusyChatSessionFailsVisibly() {
        // 对话页正在生成一条回答：此时会话会吞掉新的 send，说出去的话就没有下落。
        chat.send("先回答这一条");
        assertTrue(chat.state().generating());

        recognizer.result = "再来一句";
        say();

        assertEquals(AiVoiceSnapshot.State.FAILED, last().state());
        assertNotNull("没能送进会话必须给出原因，不能假装在思考", last().failure());
        assertEquals("再来一句", last().turns().get(0).text());
        assertTrue(last().turns().get(0).finalTranscript());
    }

    @Test
    public void stopIsIdempotentAndSilentAfterwards() {
        strategy.stop();
        assertEquals(AiVoiceSnapshot.State.ENDED, last().state());
        int afterEnded = snapshots.size();

        strategy.stop();
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0.9f);
        clock.advance(5_000);
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0f);

        assertEquals("挂断之后一个快照都不许再发", afterEnded, snapshots.size());
        assertEquals(1, capture.stops);
        assertEquals(1, playback.stops);
    }

    /** 说一句话，然后安静到阈值：这是整条链路的起点。 */
    private void say() {
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0.7f);
        clock.advance(300);
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0.5f);
        clock.advance(400);
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0f);
        clock.advance(500);
        capture.emit(FakeAudioCapture.pcm(FRAME_BYTES), 0f);
    }

    private AiVoiceSnapshot last() {
        assertTrue("至少应该收到一个快照", !snapshots.isEmpty());
        return snapshots.get(snapshots.size() - 1);
    }
}
