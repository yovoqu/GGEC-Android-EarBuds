package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.chat.AiChatState;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiMessageFailure;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceSettings;
import com.ggec.earbuds.domain.ai.port.AiAudioCapture;
import com.ggec.earbuds.domain.ai.port.AiAudioPlayback;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.AiSpeechRecognizer;
import com.ggec.earbuds.domain.ai.port.AiSpeechSynthesizer;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.voice.AiVoiceException;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 级联模式的语音会话：录音 → 静音切句 → 转写 → 交给对话会话 → 合成朗读 → 回到聆听。
 *
 * <p><b>为什么"思考"这一步直接调 {@code AiChatSession}，而不是自己发一次请求</b>：
 * 通话结束后这段对话必须原样出现在历史里，而且要和对话页共用同一条上下文、同一套停止/重试、
 * 同一份落盘。自己再写一条链路，就会出现"通话里说过的、对话页看不到"这种只有用户才发现的裂口；
 * 复用会话之后，历史、用量、上下文窗口全都不用重写（见 ai-module-v2.md §8.1）。</p>
 *
 * <p><b>为什么判"说完了"要看累积的音频和持续时间，而不是单帧电平</b>：
 * 两个方向都会误判。只看单帧，字与字之间的闭合（几十毫秒的静音）会被当成一句话的结尾，
 * 一句话被切成七八段送转写，费钱又读不懂；只看"有没有声音"，那么房间里持续的底噪会攒出一段
 * 永远不结束的音频，用户永远等不到回答。这里的规则是：一帧超过 {@link #SPEECH_LEVEL} 就算"还在说"，
 * 连续安静超过 {@code silenceTimeoutMillis} 才认为这一句结束，而且只有真正攒到过语音才允许切句——
 * 用户从头到尾没开口时，一个请求都不会发出去。</p>
 *
 * <p><b>为什么打断要先 flush 再回聆听</b>：合成是"出声"的一方，播放通道里可能还排着一段没播完的音频。
 * 如果只把状态改成 LISTENING，用户下一句已经出口，耳机里还在念上一段回答——他说的和听的对不上，
 * 整段对话就废了。所以顺序固定为：停合成 → 丢掉未播数据 → 取消还在生成的回答 → 回聆听。</p>
 *
 * <p><b>线程</b>：采集回调在采集线程、合成回调在 TTS 线程、对话状态在会话的执行器线程。
 * 三者都先投回 {@code executor}（与 {@link AiChatSession} 同构的串行执行器）再改状态，
 * 快照只有一个出口。转写与朗读是阻塞调用，走另一条 {@code blocking} 通道——
 * 它们要是占了串行执行器，打断和挂断就得排在一段几秒钟的朗读后面才生效。</p>
 */
public final class CascadedVoiceStrategy implements AiVoiceSession {

    /** 采集、播放、转写统一 16kHz：整条链路只认一种采样率，中途不重采样。 */
    public static final int SAMPLE_RATE_HZ = 16_000;

    /**
     * 判定"这一帧有人在说话"的电平门限。采集端已经做过对数压缩：
     * 安静房间约 0.05，正常说话 0.5~0.8，所以 0.18 能分开两者，又不会被换气卡住。
     */
    private static final float SPEECH_LEVEL = 0.18f;

    /** 电平变化小于这个量不重发快照：每秒几十帧的细微抖动对光晕没有意义，只会让页面白白重绘。 */
    private static final float LEVEL_EPSILON = 0.01f;

    /**
     * 判定"开始说话"之前先留住的帧数。等看见第一帧超过门限时才起录音，
     * 会把那个音节的起音削掉（"你好"变成"爱"），所以回填最近若干帧。
     */
    private static final int PRE_ROLL_FRAMES = 4;

    /** 单轮最长 30 秒：一句话说得再久也该切了，否则一次转写的上传量没有上界。 */
    private static final int MAX_UTTERANCE_BYTES = SAMPLE_RATE_HZ * 2 * 30;

    private final AiSettingsRepository settings;
    private final AiChatSession chat;
    private final AiAudioCapture capture;
    private final AiAudioPlayback playback;
    private final AiSpeechRecognizer recognizer;
    private final AiSpeechSynthesizer synthesizer;
    private final AiClock clock;
    /** 状态合并：所有字段都只在它的线程上改，快照只从它发出。 */
    private final Executor executor;
    /** 阻塞通道：转写与朗读。单线程即可——这条链路上它们本来就是先后发生的。 */
    private final Executor blocking;

    private final AiAudioCapture.Listener captureListener;

    /** 挂断从任意线程发起，必须在 {@code start} 派发之前就生效。 */
    private volatile boolean stopRequested;

    // ---- 以下字段只在 executor 线程上读写 ----

    private AiVoiceSettings voice = AiVoiceSettings.defaults();
    private String platformId = "";
    @Nullable
    private Listener listener;
    /** 已经发过 ENDED，之后的任何事件都不再往外发快照。 */
    private boolean silenced;
    private boolean ended;
    @Nullable
    private AiFailure failure;
    private Phase phase = Phase.IDLE;
    private final List<AiVoiceSnapshot.Turn> turns = new ArrayList<>();
    private float level;
    private boolean muted;
    private int turnsCreated;

    private final ByteArrayOutputStream utterance = new ByteArrayOutputStream();
    private final Deque<byte[]> preRoll = new ArrayDeque<>();
    private boolean inUtterance;
    private long lastVoicedAtMillis;

    /** 单调递增的回合号：迟到的转写/朗读结果靠它作废，避免把上一轮的回调算到这一轮头上。 */
    private long utteranceToken;
    private long speakToken;
    /** 这一轮助手回答是否已经处理过（流式增量会推很多次状态，只能接住一次）。 */
    private boolean answerHandled;
    /**
     * 我们发出去的那句话，以及发之前会话里已有的消息条数。
     * 对话会话是共享的：只认"这个位置之后出现的那句用户消息"，才不会把别人那条回答
     * （用户在对话页里还没播完的那条）当成对我们的回复念出来。
     */
    private int pendingBase;
    private String pendingText = "";
    @Nullable
    private String assistantTurnId;
    @Nullable
    private Subscription chatSubscription;

    public CascadedVoiceStrategy(
            @NonNull AiSettingsRepository settings,
            @NonNull AiChatSession chat,
            @NonNull AiAudioCapture capture,
            @NonNull AiAudioPlayback playback,
            @NonNull AiSpeechRecognizer recognizer,
            @NonNull AiSpeechSynthesizer synthesizer,
            @NonNull AiClock clock,
            @NonNull Executor executor,
            @NonNull Executor blocking) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.chat = Objects.requireNonNull(chat, "chat");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.recognizer = Objects.requireNonNull(recognizer, "recognizer");
        this.synthesizer = Objects.requireNonNull(synthesizer, "synthesizer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.blocking = Objects.requireNonNull(blocking, "blocking");
        this.captureListener = new AiAudioCapture.Listener() {
            @Override
            public void onFrame(@NonNull byte[] pcm16, float frameLevel) {
                executor.execute(() -> onFrameInternal(pcm16, frameLevel));
            }

            @Override
            public void onFailure(@NonNull AiFailureKind kind, @NonNull String detail) {
                executor.execute(() -> fail(new AiFailure(kind, detail)));
            }
        };
    }

    // ---- AiVoiceSession ----

    @Override
    public void start(@NonNull AiVoiceSettings next, @NonNull String platform, @NonNull Listener target) {
        Objects.requireNonNull(next, "settings");
        Objects.requireNonNull(platform, "platformId");
        Objects.requireNonNull(target, "listener");
        executor.execute(() -> doStart(next, platform, target));
    }

    @Override
    public void setMuted(boolean value) {
        executor.execute(() -> doSetMuted(value));
    }

    @Override
    public void setSpeakerphone(boolean speaker) {
        executor.execute(() -> doSetSpeakerphone(speaker));
    }

    @Override
    public void interrupt() {
        executor.execute(this::doInterrupt);
    }

    @Override
    public void stop() {
        stopRequested = true;
        executor.execute(this::doStop);
    }

    // ---- 生命周期 ----

    private void doStart(AiVoiceSettings next, String platform, Listener target) {
        if (stopRequested || listener != null) {
            return;
        }
        voice = next;
        platformId = platform;
        listener = target;
        publish();                       // CONNECTING

        AiFailure unusable = platformProblem();
        if (unusable != null) {
            fail(unusable);
            return;
        }
        chatSubscription = chat.observe(state -> executor.execute(() -> onChat(state)));
        try {
            playback.start(SAMPLE_RATE_HZ);
        } catch (RuntimeException broken) {
            fail(new AiFailure(AiFailureKind.UNKNOWN,
                    "音频输出通道打不开：" + detailOf(broken), broken));
            return;
        }
        capture.start(SAMPLE_RATE_HZ, captureListener);
        if (failure != null) {
            // 采集端可能同步就报了失败（没有权限），此时不要再往前走。
            return;
        }
        phase = Phase.LISTENING;
        publish();
    }

    private void doStop() {
        if (ended) {
            return;
        }
        ended = true;
        phase = Phase.OVER;
        invalidateRounds();
        releaseChat();
        bestEffort(synthesizer::stop);
        bestEffort(capture::stop);
        bestEffort(playback::stop);
        publish();                       // ENDED，最后一个快照
        silenced = true;
        listener = null;
    }

    private void doSetMuted(boolean value) {
        if (ended || failure != null) {
            return;
        }
        muted = value;
        capture.setMuted(value);
        if (!value) {
            // 静音期间攒下的半句不作数：解开静音后从"现在"重新开始判句。
            inUtterance = false;
            utterance.reset();
            preRoll.clear();
        }
        publish();
    }

    private void doSetSpeakerphone(boolean speaker) {
        if (ended) {
            return;
        }
        playback.setSpeakerphone(speaker);
    }

    private void doInterrupt() {
        if (ended || failure != null) {
            return;
        }
        if (phase != Phase.SPEAKING && phase != Phase.ANSWERING && phase != Phase.TRANSCRIBING) {
            return;
        }
        // 先让这一轮的迟到回调全部作废，再做实际的停止动作。
        speakToken++;
        utteranceToken++;
        inUtterance = false;
        utterance.reset();
        preRoll.clear();
        markTurnFinal(assistantTurnId);
        assistantTurnId = null;
        answerHandled = true;
        bestEffort(synthesizer::stop);
        bestEffort(playback::flush);
        bestEffort(chat::stop);
        finishListening();
    }

    /**
     * 失败是终态：把原因放进快照，释放设备，绝不停在一个"看起来还在听"的状态里。
     *
     * <p>这里不取消对话会话里正在生成的那次请求：它已经和这次通话无关了，
     * 让它自然结束、把回答留在历史里，比留下一段被截断的消息更有用。</p>
     */
    private void fail(@NonNull AiFailure reason) {
        if (ended) {
            return;
        }
        failure = reason;
        phase = Phase.OVER;
        invalidateRounds();
        releaseChat();
        bestEffort(synthesizer::stop);
        bestEffort(capture::stop);
        bestEffort(playback::stop);
        publish();
    }

    private void invalidateRounds() {
        utteranceToken++;
        speakToken++;
        inUtterance = false;
        utterance.reset();
        preRoll.clear();
        markTurnFinal(assistantTurnId);
        assistantTurnId = null;
        answerHandled = true;
        pendingBase = 0;
        pendingText = "";
    }

    private void releaseChat() {
        Subscription current = chatSubscription;
        chatSubscription = null;
        if (current != null) {
            current.cancel();
        }
    }

    /** 起通话前就把"跑不起来"的原因找出来：总比让用户对着光晕等三十秒强。 */
    @Nullable
    private AiFailure platformProblem() {
        AiSettings current;
        try {
            current = settings.load();
        } catch (RuntimeException unreadable) {
            return new AiFailure(AiFailureKind.STORAGE, "读取 AI 设置失败，通话无法开始。", unreadable);
        }
        if (!platformId.isBlank()) {
            Optional<AiPlatformConfig> chosen = current.platform(platformId);
            if (chosen.isEmpty()) {
                return new AiFailure(AiFailureKind.NO_PLATFORM,
                        "语音要用的平台已经不在配置里了（可能被删除）。");
            }
            if (!chosen.get().configured()) {
                return new AiFailure(AiFailureKind.NO_PLATFORM,
                        "平台「" + chosen.get().name() + "」还没有填 API Key。");
            }
        }
        if (current.activeModel().isEmpty()) {
            return current.hasConfiguredPlatform()
                    ? new AiFailure(AiFailureKind.NO_MODEL,
                            "通话的后半段要交给对话模型，但现在没有选中的可用模型。")
                    : new AiFailure(AiFailureKind.NO_PLATFORM, "还没有配置任何 AI 平台，通话无法开始。");
        }
        return null;
    }

    // ---- 上行：采集 ----

    private void onFrameInternal(byte[] pcm16, float frameLevel) {
        if (ended || failure != null || phase != Phase.LISTENING) {
            // 思考/朗读期间麦克风还在采（用来打断、也用来画电平），但这一圈声音不算进这一句。
            if (level != 0f) {
                level = 0f;
                publish();
            }
            return;
        }
        boolean levelMoved = Math.abs(frameLevel - level) >= LEVEL_EPSILON;
        level = frameLevel;
        if (muted) {
            if (levelMoved) {
                publish();
            }
            return;
        }
        long now = clock.now();
        if (frameLevel >= SPEECH_LEVEL) {
            if (!inUtterance) {
                beginUtterance();
            }
            lastVoicedAtMillis = now;
            append(pcm16);
            if (utterance.size() >= MAX_UTTERANCE_BYTES) {
                finishUtterance();
                return;
            }
            if (levelMoved) {
                publish();
            }
            return;
        }
        if (inUtterance) {
            append(pcm16);
            if (utterance.size() >= MAX_UTTERANCE_BYTES
                    || now - lastVoicedAtMillis >= voice.silenceTimeoutMillis()) {
                finishUtterance();
                return;
            }
        } else {
            rememberPreRoll(pcm16);
        }
        if (levelMoved) {
            publish();
        }
    }

    private void beginUtterance() {
        inUtterance = true;
        utterance.reset();
        for (byte[] frame : preRoll) {
            utterance.write(frame, 0, frame.length);
        }
        preRoll.clear();
    }

    private void append(byte[] pcm16) {
        if (pcm16.length == 0) {
            return;
        }
        utterance.write(pcm16, 0, pcm16.length);
    }

    private void rememberPreRoll(byte[] pcm16) {
        if (pcm16.length == 0) {
            return;
        }
        preRoll.addLast(pcm16.clone());
        while (preRoll.size() > PRE_ROLL_FRAMES) {
            preRoll.removeFirst();
        }
    }

    /** 一句话结束：把累积的音频交给转写。空音频直接回聆听，不发请求。 */
    private void finishUtterance() {
        if (!inUtterance) {
            return;
        }
        inUtterance = false;
        preRoll.clear();
        byte[] pcm = utterance.toByteArray();
        utterance.reset();
        if (pcm.length == 0) {
            publish();
            return;
        }
        long token = ++utteranceToken;
        answerHandled = false;
        assistantTurnId = null;
        String userTurnId = nextTurnId();
        // 先占一条"识别中"的用户发言：转写回来后原地补文字，界面不会跳出一句空话再消失。
        turns.add(new AiVoiceSnapshot.Turn(userTurnId, true, "", false, clock.now()));
        phase = Phase.TRANSCRIBING;
        publish();
        String model = voice.transcriptionModel();
        blocking.execute(() -> {
            String text;
            try {
                text = recognizer.transcribe(pcm, SAMPLE_RATE_HZ, model);
            } catch (AiVoiceException expected) {
                executor.execute(() -> onTranscribed(token, userTurnId, null, expected.failure()));
                return;
            } catch (RuntimeException unexpected) {
                AiFailure folded = new AiFailure(AiFailureKind.UNKNOWN,
                        "语音识别出错：" + detailOf(unexpected), unexpected);
                executor.execute(() -> onTranscribed(token, userTurnId, null, folded));
                return;
            }
            String cleaned = text == null ? "" : text.strip();
            executor.execute(() -> onTranscribed(token, userTurnId, cleaned, null));
        });
    }

    /**
     * 转写这一步失败时，别把平台对聊天的判断原样抛给用户。
     *
     * <p>用 DeepSeek 这种"只有对话接口"的平台开 CASCADED，转写一定 404。原始文案是
     * 「没有找到该模型或接口，请检查模型名和接口地址」——但平台和模型都没错，是这条路走不通。
     * 用户照那句话去改设置只会白费，所以这里换成能照做的说法。</p>
     */
    static AiFailure transcriptionFailure(AiFailure error) {
        return stepFailure(error, "这个平台没有提供语音转写接口。换一个支持转写的平台，或在设置里改用全双工实时对话。");
    }

    /**
     * 链路上某一步缺失时的说法。转写与合成缺的是两件不同的事，用户要改的东西也不一样，
     * 所以文案由调用方给，这里只判断"是不是这条路根本不存在"。
     */
    static AiFailure stepFailure(AiFailure error, String missingEndpoint) {
        if (error.kind() == AiFailureKind.NOT_FOUND
                || error.kind() == AiFailureKind.UNSUPPORTED_CAPABILITY) {
            return new AiFailure(error.kind(), missingEndpoint);
        }
        return error;
    }

    private void onTranscribed(long token, String userTurnId,
                               @Nullable String text, @Nullable AiFailure error) {
        if (token != utteranceToken || ended || failure != null) {
            return;
        }
        if (error != null) {
            fail(transcriptionFailure(error));
            return;
        }
        if (text == null || text.isEmpty()) {
            // 没听到内容：撤掉那条占位发言，安静地回到聆听，不打扰用户。
            removeTurn(userTurnId);
            finishListening();
            return;
        }
        replaceTurn(userTurnId, text, true);
        if (chat.state().generating()) {
            // 同一条会话正在生成另一条回答：此刻 send 会被会话直接丢掉，说出去的这句话就没了下落。
            // 与其假装在思考，不如把它变成一个用户看得见的原因。
            fail(new AiFailure(AiFailureKind.UNKNOWN,
                    "对话页正在生成上一条回答，这一句没有发出去；等它答完再说一次。"));
            return;
        }
        AiChatState before = chat.state();
        pendingBase = before.conversation().messages().size();
        pendingText = text;
        phase = Phase.ANSWERING;
        answerHandled = false;
        publish();
        // 复用同一条会话：上下文、停止、重试、落盘都由它负责，通话记录因此能出现在历史里。
        chat.send(text);
    }

    /** 我们这句是否真的进了会话（位置在发送前那条之后，且文本一致）。 */
    private boolean landedInChat(AiConversation conversation) {
        List<AiMessage> messages = conversation.messages();
        if (messages.size() <= pendingBase) {
            return false;
        }
        for (int i = pendingBase; i < messages.size(); i++) {
            AiMessage message = messages.get(i);
            if (message.user() && message.text().equals(pendingText)) {
                return true;
            }
        }
        return false;
    }

    // ---- 下行：对话 → 朗读 ----

    private void onChat(AiChatState state) {
        if (ended || failure != null || phase != Phase.ANSWERING || answerHandled) {
            return;
        }
        AiMessage last = state.conversation().last().orElse(null);
        if (last == null) {
            return;
        }
        if (!landedInChat(state.conversation())) {
            // 这句还没进会话（或根本没进去）：此刻任何回答都不是给我们的，别抢着念。
            return;
        }
        if (last.user()) {
            if (!state.generating()) {
                // 会话只是把状态推了一遍，说明请求根本没发出去（通常是没选中模型）。
                answerHandled = true;
                fail(new AiFailure(AiFailureKind.NO_MODEL,
                        "这一句要交给对话模型回答，但当前没有选中的可用模型。"));
            }
            return;
        }
        if (last.streaming()) {
            if (!last.text().isBlank()) {
                showAnswer(last.text(), false);
            }
            return;
        }
        switch (last.status()) {
            case COMPLETE -> {
                answerHandled = true;
                String answer = last.text().strip();
                if (answer.isEmpty()) {
                    // 模型只给了推理没给正文：没什么可念的，回聆听，别让用户干等。
                    finishListening();
                    return;
                }
                showAnswer(answer, true);
                speak(answer);
            }
            case ERROR -> {
                answerHandled = true;
                AiMessageFailure why = last.failure();
                fail(why == null
                        ? new AiFailure(AiFailureKind.UNKNOWN, "对话请求失败了。")
                        : new AiFailure(why.kind(), why.detail()));
            }
            case STOPPED -> {
                answerHandled = true;
                finishListening();
            }
            default -> {
                // ERROR/STOPPED/COMPLETE/STREAMING 之外没有别的状态；真出现也先什么都不做。
            }
        }
    }

    private void showAnswer(String text, boolean isFinal) {
        if (assistantTurnId == null) {
            assistantTurnId = nextTurnId();
            turns.add(new AiVoiceSnapshot.Turn(assistantTurnId, false, text, isFinal, clock.now()));
        } else {
            replaceTurn(assistantTurnId, text, isFinal);
        }
        if (!isFinal) {
            publish();
        }
    }

    private void speak(String answer) {
        long token = ++speakToken;
        String voiceName = voice.synthesisVoice();
        blocking.execute(() -> {
            try {
                synthesizer.speak(answer, voiceName, new AiSpeechSynthesizer.Listener() {
                    @Override
                    public void onStarted() {
                        executor.execute(() -> onSpeakStarted(token));
                    }

                    @Override
                    public void onDone() {
                        executor.execute(() -> onSpeakDone(token));
                    }

                    @Override
                    public void onFailure(@NonNull AiFailure reason) {
                        executor.execute(() -> onSpeakFailed(token, reason));
                    }
                });
            } catch (AiVoiceException expected) {
                executor.execute(() -> onSpeakFailed(token, expected.failure()));
            } catch (RuntimeException unexpected) {
                AiFailure folded = new AiFailure(AiFailureKind.UNKNOWN,
                        "语音合成出错：" + detailOf(unexpected), unexpected);
                executor.execute(() -> onSpeakFailed(token, folded));
            }
        });
    }

    /** 真正出声了才切 SPEAKING：在此之前仍是"synthesizer 在准备"，页面上是思考态。 */
    private void onSpeakStarted(long token) {
        if (token != speakToken || ended || failure != null) {
            return;
        }
        phase = Phase.SPEAKING;
        publish();
    }

    private void onSpeakDone(long token) {
        if (token != speakToken || ended || failure != null) {
            return;
        }
        finishListening();
    }

    private void onSpeakFailed(long token, AiFailure reason) {
        if (token != speakToken || ended || failure != null) {
            return;
        }
        fail(stepFailure(reason, "这个平台没有提供语音合成接口。换一个支持合成的平台，或在设置里改用全双工实时对话。"));
    }

    private void finishListening() {
        phase = Phase.LISTENING;
        level = 0f;
        publish();
    }

    // ---- 快照 ----

    private void publish() {
        if (silenced) {
            return;
        }
        Listener target = listener;
        if (target == null) {
            return;
        }
        AiVoiceSnapshot snapshot = new AiVoiceSnapshot(
                stateOf(), voice.mode(), turns, shownLevel(), failure);
        try {
            target.onSnapshot(snapshot);
        } catch (RuntimeException fromPage) {
            // 页面回调是外部代码：它抛错属于界面缺陷，不该把通话的状态机带停。
            // 与 AiChatSession 对观察者的处理保持一致。
        }
    }

    private AiVoiceSnapshot.State stateOf() {
        if (failure != null) {
            return AiVoiceSnapshot.State.FAILED;
        }
        if (ended) {
            return AiVoiceSnapshot.State.ENDED;
        }
        return switch (phase) {
            case IDLE -> AiVoiceSnapshot.State.CONNECTING;
            case LISTENING -> muted ? AiVoiceSnapshot.State.MUTED : AiVoiceSnapshot.State.LISTENING;
            case TRANSCRIBING, ANSWERING -> AiVoiceSnapshot.State.THINKING;
            case SPEAKING -> AiVoiceSnapshot.State.SPEAKING;
            case OVER -> AiVoiceSnapshot.State.ENDED;
        };
    }

    /** 只有在听的时候电平才有意义：朗读时麦克风听到的是自己的回声，让光晕跟着跳会露馅。 */
    private float shownLevel() {
        return phase == Phase.LISTENING ? level : 0f;
    }

    // ---- 发言记录 ----

    private String nextTurnId() {
        turnsCreated++;
        return "turn-" + turnsCreated;
    }

    private void replaceTurn(String turnId, String text, boolean isFinal) {
        for (int i = 0; i < turns.size(); i++) {
            AiVoiceSnapshot.Turn turn = turns.get(i);
            if (turn.id().equals(turnId)) {
                turns.set(i, new AiVoiceSnapshot.Turn(turnId, turn.user(), text, isFinal,
                        turn.atMillis()));
                return;
            }
        }
    }

    private void removeTurn(String turnId) {
        turns.removeIf(turn -> turn.id().equals(turnId));
    }

    /** 被打断的那一轮也要收尾：留着 finalTranscript=false 会让界面一直画"还在写"。 */
    private void markTurnFinal(@Nullable String turnId) {
        if (turnId == null) {
            return;
        }
        for (int i = 0; i < turns.size(); i++) {
            AiVoiceSnapshot.Turn turn = turns.get(i);
            if (turn.id().equals(turnId) && !turn.finalTranscript()) {
                turns.set(i, new AiVoiceSnapshot.Turn(turnId, turn.user(), turn.text(), true,
                        turn.atMillis()));
                return;
            }
        }
    }

    /**
     * 释放路径上一步一步做：某个设备关不掉不能挡住另外两个。
     * 这类失败发生时通话已经结束（或已经失败），再往快照里塞第二个原因只会盖掉真正的那个。
     */
    private static void bestEffort(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException alreadyReported) {
            // 见方法注释。
        }
    }

    private static String detailOf(Throwable error) {
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail;
    }

    /** 内部阶段。对外只暴露 {@link AiVoiceSnapshot.State}，这个枚举不进领域。 */
    private enum Phase {
        IDLE,
        LISTENING,
        TRANSCRIBING,
        ANSWERING,
        SPEAKING,
        OVER
    }
}
