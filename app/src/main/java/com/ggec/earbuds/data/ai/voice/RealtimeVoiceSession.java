package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiAudioCapture;
import com.ggec.earbuds.domain.ai.port.AiAudioPlayback;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiRealtimeLink;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.yovo.lib_ai.catalog.YoPlatformCatalog;
import com.yovo.lib_ai.catalog.YoPlatformDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 语音交流模式的会话：一条 WebSocket 同时送上行音频、收下行音频与文字增量。
 *
 * <p><b>为什么这条链路不需要"判句"</b>：APP 侧不做转写，也不等静音攒句；断句是服务端做的
 * （{@code turn_detection: server_vad}），它直接告诉我们"人开口了/说完了"。
 * 客户端插一脚只会和服务端的判定打架——两边都判，就会出现
 * "服务端觉得还在说、我们却已经切了句"的错位。</p>
 *
 * <p><b>为什么采集从连上一直采到挂断，而不是只在聆听时采</b>：服务端的 VAD 要靠连续音频才能判断
 * 用户什么时候插话。朗读期间如果停掉上行，用户说"等等"它听不见，这轮回答只能等它自己念完。
 * 所以整通电话只做一次 {@code capture.start}，静音时才把上行关掉
 * （{@code AiAudioCapture#setMuted} 只停上行、保留电平）。</p>
 *
 * <p><b>为什么用户一开口就要 flush + 取消</b>：服务端报 {@code speech_started} 时，上一轮回答的音频
 * 还可能在路上。只把状态改回 LISTENING 不解决问题——下一块音频到达又会把状态推回 SPEAKING，
 * 用户会觉得"它非要念完"。所以顺序是：丢掉未播数据 → 让服务端停止生成 → 收尾当前这条发言 → 回聆听。</p>
 *
 * <p><b>为什么文字要"累积"而不是覆盖</b>：下行是增量（{@code delta}），每次只有几个字。
 * 直接替换会让界面上的一句话反复被最后一个字覆盖；累积之后 {@code finalTranscript} 才是
 * "这句话说完了"的信号（服务端 {@code response.done} 才置 true）。</p>
 *
 * <p><b>线程</b>：链路的回调在它自己的线程（lib_ai 那边是一条串行回调线程），采集在采集线程，
 * 挂断与打断来自页面线程。四条线都先投回构造时注入的 {@code executor}
 * （一条串行执行器），所有字段只在它上面改，
 * {@link AiVoiceSnapshot} 只从它发出——页面因此不需要任何同步。</p>
 */
public final class RealtimeVoiceSession implements AiVoiceSession {

    /** 采集统一 16kHz：实时语音的输入按这个档位工作。 */
    public static final int INPUT_SAMPLE_RATE_HZ = PcmResampler.INPUT_RATE_HZ;

    /** 下行音频原生 24kHz：直接交给播放器，不再二次重采样。 */
    public static final int OUTPUT_SAMPLE_RATE_HZ = PcmResampler.OUTPUT_RATE_HZ;

    /** 电平变化小于这个量不重发快照：每秒几十帧的抖动对光晕没有意义，只会让页面白白重绘。 */
    private static final float LEVEL_EPSILON = 0.01f;

    private final AiSettingsRepository settings;
    private final AiAudioCapture capture;
    private final AiAudioPlayback playback;
    private final AiRealtimeLink link;
    private final AiClock clock;
    /** 状态合并：所有字段都只在它的线程上改，快照只从它发出。 */
    private final Executor executor;

    private final PcmResampler resampler = new PcmResampler();
    private final AiAudioCapture.Listener captureListener;
    private final AiRealtimeLink.Listener linkListener;

    /** 挂断从任意线程发起，必须在 {@code start} 派发之前就生效。 */
    private volatile boolean stopRequested;

    // ---- 以下字段只在 executor 线程上读写 ----

    private AiSelection selection;
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

    /** 当前这一轮助手发言的 id；为空表示还没开始累积文字。 */
    @Nullable
    private String assistantTurnId;
    /** 播放通道只开一次：每次音频块都 start 会把刚播的几百毫秒清掉。 */
    private boolean playbackStarted;

    public RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiAudioCapture capture,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.link = Objects.requireNonNull(link, "link");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.captureListener = new AiAudioCapture.Listener() {
            @Override
            public void onFrame(@NonNull byte[] pcm16, float frameLevel) {
                executor.execute(() -> onFrameInternal(pcm16, frameLevel));
            }

            @Override
            public void onFailure(@NonNull AiFailureKind kind, @NonNull String detail) {
                executor.execute(() -> fail(new AiFailure(kind, "麦克风出错：" + detail)));
            }
        };
        this.linkListener = new AiRealtimeLink.Listener() {
            @Override
            public void onOpened() {
                executor.execute(RealtimeVoiceSession.this::onLinkOpened);
            }

            @Override
            public void onAssistantText(@NonNull String delta) {
                // 必须显式限定到外层：这个匿名类自己也有同名方法，不限定就会调回自己（无限递归）。
                executor.execute(() -> RealtimeVoiceSession.this.onAssistantText(delta));
            }

            @Override
            public void onAssistantAudio(@NonNull byte[] pcm16) {
                executor.execute(() -> RealtimeVoiceSession.this.onAssistantAudio(pcm16));
            }

            @Override
            public void onUserSpeechStarted() {
                executor.execute(RealtimeVoiceSession.this::onUserSpeechStarted);
            }

            @Override
            public void onUserSpeechStopped() {
                executor.execute(RealtimeVoiceSession.this::onUserSpeechStopped);
            }

            @Override
            public void onResponseDone() {
                executor.execute(RealtimeVoiceSession.this::onResponseDone);
            }

            @Override
            public void onFailure(@NonNull AiFailure reason) {
                executor.execute(() -> fail(reason));
            }
        };
    }

    // ---- AiVoiceSession ----

    @Override
    public void start(@NonNull AiSelection next, @NonNull Listener target) {
        Objects.requireNonNull(next, "selection");
        Objects.requireNonNull(target, "listener");
        executor.execute(() -> doStart(next, target));
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

    private void doStart(AiSelection next, Listener target) {
        if (stopRequested || listener != null) {
            return;
        }
        selection = next;
        listener = target;
        publish();                       // CONNECTING

        RealtimePlan plan;
        try {
            plan = planFor();
        } catch (AiFailure unusable) {
            fail(unusable);
            return;
        }
        try {
            // 打开是异步的：这一行返回时还没连上，结果走 linkListener（OPENED 或 FAILED）。
            // 地址只给到平台主机基址：wss 的拼法（/v1/realtime、模型名转义）属于协议细节，
            // 留在 lib_ai 那一侧，这一层不重复拼一遍。
            link.open(new AiRealtimeLink.Config(plan.baseUrl(), plan.apiKey(), plan.model(),
                    INPUT_SAMPLE_RATE_HZ, OUTPUT_SAMPLE_RATE_HZ), linkListener);
        } catch (RuntimeException broken) {
            fail(new AiFailure(AiFailureKind.UNKNOWN,
                    "实时语音通道打不开：" + detailOf(broken), broken));
        }
    }

    /**
     * 连上之后才开采集：先把服务端那边准备好，再让麦克风出声。
     * 反过来的话，握手失败时麦克风已经开着，用户会看到"在听"却永远等不到回答。
     */
    private void onLinkOpened() {
        if (ended || failure != null || phase != Phase.IDLE) {
            return;
        }
        resampler.reset();
        capture.start(INPUT_SAMPLE_RATE_HZ, captureListener);
        if (failure != null || ended) {
            // 采集端可能同步就报了失败（没有录音权限），此时不要往前走到 LISTENING。
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
        markTurnFinal(assistantTurnId);
        assistantTurnId = null;
        bestEffort(link::close);
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
        publish();
    }

    private void doSetSpeakerphone(boolean speaker) {
        if (ended) {
            return;
        }
        playback.setSpeakerphone(speaker);
    }

    private void doInterrupt() {
        if (ended || failure != null || phase == Phase.IDLE) {
            // 还在 CONNECTING 时打断没有意义：链路没建好，也就没有"正在生成的回答"可停。
            return;
        }
        // 先让服务端停：只丢本地缓存的话，下一块音频紧接着又到，界面上会看起来"打断失败"。
        bestEffort(link::cancelResponse);
        bestEffort(playback::flush);
        markTurnFinal(assistantTurnId);
        assistantTurnId = null;
        phase = Phase.LISTENING;
        level = 0f;
        publish();
    }

    /**
     * 失败是终态：原因进快照、设备全部释放，绝不停在一个"看起来还在听"的状态里。
     */
    private void fail(@NonNull AiFailure reason) {
        if (ended || failure != null) {
            // 只认第一个原因：链路失败通常连着采集和播放各报一次，后到的都是同一个坏掉的连接。
            return;
        }
        failure = reason;
        phase = Phase.OVER;
        markTurnFinal(assistantTurnId);
        assistantTurnId = null;
        bestEffort(link::close);
        bestEffort(capture::stop);
        bestEffort(playback::stop);
        publish();
    }

    // ---- 上行 ----

    private void onFrameInternal(byte[] pcm16, float frameLevel) {
        if (ended || failure != null || pcm16 == null) {
            return;
        }
        // 光晕只在聆听时跟着声音动：朗读时麦克风听到的是自己的回声，跟着跳会露馅。
        if (phase == Phase.LISTENING) {
            if (Math.abs(frameLevel - level) >= LEVEL_EPSILON) {
                level = frameLevel;
                publish();
            }
        }
        if (muted) {
            return;
        }
        // 16k → 24k：服务端按 24k 解释 pcm16，送 16k 过去会变成慢了 1.5 倍的声音。
        byte[] upsampled = resampler.resample(pcm16);
        if (upsampled.length == 0) {
            return;
        }
        try {
            link.appendAudio(upsampled);
        } catch (RuntimeException lost) {
            // 上行失败由链路的错误事件负责上报；这里再抛只会把采集线程打断。
        }
    }

    // ---- 下行 ----

    private void onAssistantText(String delta) {
        if (ended || failure != null) {
            return;
        }
        String text = delta == null ? "" : delta;
        if (text.isEmpty()) {
            return;
        }
        if (assistantTurnId == null) {
            assistantTurnId = nextTurnId();
            turns.add(new AiVoiceSnapshot.Turn(assistantTurnId, false, text, false, clock.now()));
        } else {
            appendToTurn(assistantTurnId, text);
        }
        // 先有字、后有声是常态（文字增量比音频块到得早），此时页面应该显示"在回答"而不是"在听"。
        if (phase != Phase.SPEAKING) {
            phase = Phase.THINKING;
        }
        publish();
    }

    private void onAssistantAudio(byte[] pcm16) {
        if (ended || failure != null || pcm16 == null || pcm16.length == 0) {
            return;
        }
        if (!playbackStarted) {
            try {
                playback.start(OUTPUT_SAMPLE_RATE_HZ);
                playbackStarted = true;
            } catch (RuntimeException broken) {
                fail(new AiFailure(AiFailureKind.UNKNOWN,
                        "音频输出通道打不开：" + detailOf(broken), broken));
                return;
            }
        }
        try {
            playback.write(pcm16);
        } catch (RuntimeException broken) {
            // 单块写失败是播放侧的抖动，不值得让整通电话失败：后面的块会接着写。
        }
        boolean entered = phase != Phase.SPEAKING;
        phase = Phase.SPEAKING;
        if (entered) {
            publish();
        }
    }

    /**
     * 服务端 VAD 判定用户开口：这是插话，不是"接着说"。
     */
    private void onUserSpeechStarted() {
        if (ended || failure != null) {
            return;
        }
        bestEffort(playback::flush);
        // 同时让服务端停止生成：不取消的话，下一块音频到达又会把状态推回 SPEAKING。
        bestEffort(link::cancelResponse);
        markTurnFinal(assistantTurnId);
        assistantTurnId = null;
        phase = Phase.LISTENING;
        level = 0f;
        publish();
    }

    private void onUserSpeechStopped() {
        if (ended || failure != null) {
            return;
        }
        if (phase == Phase.LISTENING) {
            // 人说完，接下来轮到服务端生成：先切思考，页面不会在等待期间还画着"在听"。
            phase = Phase.THINKING;
            publish();
        }
    }

    private void onResponseDone() {
        if (ended || failure != null) {
            return;
        }
        markTurnFinal(assistantTurnId);
        assistantTurnId = null;
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
                stateOf(), turns, shownLevel(), failure);
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
            case THINKING -> AiVoiceSnapshot.State.THINKING;
            case SPEAKING -> AiVoiceSnapshot.State.SPEAKING;
            case OVER -> AiVoiceSnapshot.State.ENDED;
        };
    }

    private float shownLevel() {
        return phase == Phase.LISTENING ? level : 0f;
    }

    // ---- 发言记录 ----

    private String nextTurnId() {
        turnsCreated++;
        return "turn-" + turnsCreated;
    }

    private void appendToTurn(String turnId, String delta) {
        for (int i = 0; i < turns.size(); i++) {
            AiVoiceSnapshot.Turn turn = turns.get(i);
            if (turn.id().equals(turnId)) {
                turns.set(i, new AiVoiceSnapshot.Turn(turnId, turn.user(), turn.text() + delta, false,
                        turn.atMillis()));
                return;
            }
        }
    }

    /** 被打断或被挂断的那一轮也要收尾：留着 finalTranscript=false 会让界面一直画"还在写"。 */
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

    // ---- 连接目标 ----

    /**
     * 找到这次通话该连哪台主机、用哪个模型。
     *
     * <p>为什么模型必须在开连接之前就确定：Realtime 的模型是端点地址的一部分
     * （{@code /v1/realtime?model=…}，由 lib_ai 拼），没有"连上再换模型"这回事。
     * 所以缺模型时宁可当场失败，也不要先连一条注定会被服务端拒掉的连接。</p>
     */
    @NonNull
    private RealtimePlan planFor() {
        AiSettings current;
        try {
            current = settings.load();
        } catch (RuntimeException unreadable) {
            throw new AiFailure(AiFailureKind.STORAGE, "读取 AI 设置失败，通话无法开始。", unreadable);
        }
        String model = selection.modelId();
        if (model.isBlank()) {
            throw new AiFailure(AiFailureKind.NO_MODEL, "语音交流要先选一个实时模型。");
        }
        AiPlatformConfig platform = current.platform(selection.platformId()).orElseThrow(() ->
                new AiFailure(AiFailureKind.NO_PLATFORM,
                        "语音设置里指定的平台已经不在配置中（可能被删除了）。"));
        if (!platform.configured()) {
            throw new AiFailure(AiFailureKind.NO_PLATFORM,
                    "平台「" + platform.name() + "」还没有填 API Key。");
        }
        String raw = platform.baseUrl();
        if (raw == null || raw.isBlank()) {
            raw = defaultBaseUrl(platform.templateId());
        }
        if (raw == null || raw.isBlank()) {
            throw new AiFailure(AiFailureKind.BAD_REQUEST,
                    "平台「" + platform.name() + "」还没有填写主机地址。");
        }
        String baseUrl;
        try {
            // 用户可能把 /v1 甚至整条 /chat/completions 粘进来，先按平台规则收成主机基址。
            baseUrl = YoPlatformCatalog.normalizeBaseUrl(raw);
        } catch (IllegalArgumentException malformed) {
            throw new AiFailure(AiFailureKind.BAD_REQUEST, detailOf(malformed), malformed);
        }
        return new RealtimePlan(baseUrl, platform.apiKey(), model);
    }

    @Nullable
    private static String defaultBaseUrl(String templateId) {
        YoPlatformDescriptor descriptor = YoPlatformCatalog.standard().find(templateId);
        return descriptor == null ? null : descriptor.defaultBaseUrl();
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

    @NonNull
    private static String detailOf(Throwable error) {
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail;
    }

    /** 一次通话要连的目标：主机基址、密钥、模型。只在开工前算一次，之后不再读设置。 */
    private record RealtimePlan(String baseUrl, String apiKey, String model) {
    }

    /** 内部阶段。对外只暴露 {@link AiVoiceSnapshot.State}，这个枚举不进领域。 */
    private enum Phase {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
        OVER
    }
}
