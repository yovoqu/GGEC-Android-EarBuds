package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.port.AiAudioCapture;
import com.ggec.earbuds.domain.ai.port.AiAudioPlayback;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiCredentialStore;
import com.ggec.earbuds.domain.ai.port.AiRealtimeInput;
import com.ggec.earbuds.domain.ai.port.AiRealtimeLink;
import com.ggec.earbuds.domain.ai.port.AiRealtimeOutput;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceCallEffect;
import com.ggec.earbuds.domain.ai.voice.VoiceCallEvent;
import com.ggec.earbuds.domain.ai.voice.VoiceCallReducer;
import com.ggec.earbuds.domain.ai.voice.VoiceCallState;
import com.ggec.earbuds.domain.ai.voice.VoiceCallTrace;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplexPolicy;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 语音通话 actor：唯一职责是串行处理 {@link VoiceCallEvent}，调用纯 {@link VoiceCallReducer}
 * 算下一状态与副作用，再按顺序执行 effects。
 *
 * <p>状态和 IO 分开之后，任何时序问题都能在 reducer 单测里复现，真机 trace 也能直接回放。
 * 采集/播放线程只负责投事件，不直接改状态。</p>
 *
 * <p><b>双工由事实决定</b>：装配根请求一个双工方式；采集真正打开后查询
 * {@link AiAudioCapture#echoCancellationActive()}，用 {@code DuplexResolved} 落到状态机。
 * AEC 没挂上就只能半双工，否则扬声器声音会被麦克风收回去，服务端 VAD 判成用户开口，
 * 助手自问自答（实机已验证：一句输入连答 6 轮）。</p>
 */
public final class RealtimeVoiceSession implements AiVoiceSession {

    public static final int INPUT_SAMPLE_RATE_HZ = PcmResampler.INPUT_RATE_HZ;
    public static final int OUTPUT_SAMPLE_RATE_HZ = PcmResampler.OUTPUT_RATE_HZ;

    private final AiSettingsRepository settings;
    private final AiCredentialStore credentials;
    private final AiAudioCapture capture;
    private final AiAudioPlayback playback;
    private final AiRealtimeLink link;
    private final AiClock clock;
    /** 所有状态字段只在它的线程上读写。 */
    private final Executor executor;
    private final ScheduledExecutorService drainScheduler;
    private final VoiceDuplex duplex;
    /** 输出是否私密路由（耳机/蓝牙/USB）；私密时无需 AEC 也能全双工。 */
    private final boolean privateOutput;
    private final VoiceCallTrace trace = new VoiceCallTrace();

    private final PcmResampler resampler = new PcmResampler();
    private final AiAudioCapture.Listener captureListener;
    private final AiRealtimeLink.Listener linkListener;

    /** 挂断从任意线程发起，必须在 start 派发之前就生效。 */
    private volatile boolean stopRequested;

    // ---- 只在 executor 线程上读写 ----
    @Nullable
    private Listener listener;
    private boolean silenced;
    private VoiceCallReducer.Model model;
    @Nullable
    private ScheduledFuture<?> drainFuture;

    public RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiCredentialStore credentials,
            @NonNull AiAudioCapture capture,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor,
            @NonNull VoiceDuplex duplex) {
        this(settings, credentials, capture, playback, link, clock, executor, duplex,
                false, newScheduler());
    }

    public RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiCredentialStore credentials,
            @NonNull AiAudioCapture capture,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor,
            @NonNull VoiceDuplex duplex,
            boolean privateOutput) {
        this(settings, credentials, capture, playback, link, clock, executor, duplex,
                privateOutput, newScheduler());
    }

    private RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiCredentialStore credentials,
            @NonNull AiAudioCapture capture,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor,
            @NonNull VoiceDuplex duplex,
            boolean privateOutput,
            @NonNull ScheduledExecutorService drainScheduler) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.link = Objects.requireNonNull(link, "link");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.duplex = Objects.requireNonNull(duplex, "duplex");
        this.privateOutput = privateOutput;
        this.drainScheduler = Objects.requireNonNull(drainScheduler, "drainScheduler");
        this.model = VoiceCallReducer.Model.initial(duplex);
        this.captureListener = new AiAudioCapture.Listener() {
            @Override
            public void onFrame(@NonNull byte[] pcm16, float frameLevel) {
                dispatch(() -> onFrameInternal(pcm16, frameLevel));
            }

            @Override
            public void onFailure(@NonNull AiFailureKind kind, @NonNull String detail) {
                dispatch(() -> apply(new VoiceCallEvent.Failed(
                        new AiFailure(kind, detail.isEmpty() ? null : detail))));
            }
        };
        this.linkListener = output -> dispatch(() -> onLinkOutput(output));
    }

    /**
     * 投事件到状态机线程。
     *
     * <p>挂断之后采集线程和链路线程还会继续回调（它们各自有自己的停止延迟），
     * 这时候执行器可能已经关闭。那种拒绝不是错误，是"这通电话已经结束了"，
     * 所以直接丢弃——绝不能让异常冒到采集线程，那会把 AudioRecord 的读循环打断。</p>
     */
    private void dispatch(@NonNull Runnable work) {
        if (stopRequested) {
            return;
        }
        try {
            executor.execute(work);
        } catch (RuntimeException shuttingDown) {
            // 见方法注释。
        }
    }

    // ---- AiVoiceSession ----

    @Override
    public void start(@NonNull Listener target) {
        Objects.requireNonNull(target, "listener");
        dispatch(() -> doStart(target));
    }

    @Override
    public void setMuted(boolean value) {
        dispatch(() -> apply(new VoiceCallEvent.Muted(value)));
    }

    @Override
    public void setDuplex(@NonNull VoiceDuplex duplex) {
        Objects.requireNonNull(duplex, "duplex");
        dispatch(() -> apply(new VoiceCallEvent.SetDuplex(duplex)));
    }

    @Override
    public void setSpeakerphone(boolean speaker) {
        dispatch(() -> playback.setSpeakerphone(speaker));
    }

    @Override
    public void interrupt() {
        dispatch(() -> apply(new VoiceCallEvent.Interrupt()));
    }

    @Override
    public void stop() {
        // 先置位再投递：挂断之后到达的采集/链路回调都会被 dispatch 直接丢弃。
        stopRequested = true;
        try {
            executor.execute(this::doStop);
        } catch (RuntimeException shuttingDown) {
            // 执行器已关：状态机不会再收到任何事件，无需再收尾。
        }
    }

    // ---- 生命周期 ----

    private void doStart(Listener target) {
        if (stopRequested || listener != null || model.terminal()) {
            return;
        }
        listener = target;
        RealtimePlan plan;
        try {
            plan = planFor();
        } catch (AiFailure unusable) {
            apply(new VoiceCallEvent.Failed(unusable));
            return;
        }
        apply(new VoiceCallEvent.Start());
        try {
            link.open(new AiRealtimeLink.Config(plan.baseUrl(), plan.apiKey(), plan.model(),
                    duplex.streamsWhileAssistantSpeaks()), linkListener);
        } catch (RuntimeException broken) {
            apply(new VoiceCallEvent.Failed(new AiFailure(AiFailureKind.UNKNOWN,
                    detailOf(broken), broken)));
        }
    }

    private void doStop() {
        apply(new VoiceCallEvent.Stop());
        silenced = true;
        listener = null;
        drainScheduler.shutdownNow();
    }

    private void onLinkOutput(@NonNull AiRealtimeOutput output) {
        switch (output) {
            case AiRealtimeOutput.Opened ignored -> apply(new VoiceCallEvent.Opened());
            case AiRealtimeOutput.AssistantText text ->
                    apply(new VoiceCallEvent.AssistantText(text.delta()));
            case AiRealtimeOutput.AssistantAudio audio -> apply(new VoiceCallEvent.AssistantAudio(
                    audio.pcm16(), audio.sampleRateHz(), audio.itemId(), audio.contentIndex()));
            case AiRealtimeOutput.UserSpeechStarted ignored ->
                    apply(new VoiceCallEvent.SpeechStarted());
            case AiRealtimeOutput.UserSpeechStopped ignored ->
                    apply(new VoiceCallEvent.SpeechStopped());
            case AiRealtimeOutput.UserTranscript transcript ->
                    apply(new VoiceCallEvent.UserTranscript(transcript.transcript(), transcript.itemId()));
            case AiRealtimeOutput.ResponseDone ignored -> apply(new VoiceCallEvent.ResponseDone());
            case AiRealtimeOutput.Failed failed -> apply(new VoiceCallEvent.Failed(failed.failure()));
        }
    }

    private void onFrameInternal(byte[] pcm16, float frameLevel) {
        apply(new VoiceCallEvent.CaptureFrame(frameLevel));
        if (model.terminal() || pcm16 == null || pcm16.length == 0) {
            return;
        }
        // 上行线速率由方言决定：通义 16k、OpenAI 24k。相同就直接送，不白跑一次重采样。
        int wireRate = link.inputWireSampleRateHz();
        byte[] payload;
        if (wireRate == INPUT_SAMPLE_RATE_HZ) {
            payload = pcm16;
        } else {
            payload = resampler.resample(pcm16);
        }
        if (payload.length == 0) {
            return;
        }
        try {
            link.send(new AiRealtimeInput.Audio(payload, wireRate));
        } catch (RuntimeException lost) {
            // 上行失败由链路的错误事件负责上报；这里再抛只会把采集线程打断。
        }
    }

    // ---- actor ----

    private void apply(@NonNull VoiceCallEvent event) {
        long now = clock.now();
        VoiceCallState before = model.state();
        VoiceCallReducer.Transition transition = VoiceCallReducer.reduce(model, event, now);
        model = transition.model();
        if (before != model.state() || !transition.effects().isEmpty()) {
            trace.record(now, before, event, model.state());
        }
        for (VoiceCallEffect effect : transition.effects()) {
            execute(effect);
        }
    }

    /** 供测试与现场排障导出：最近的状态转移轨迹。 */
    @NonNull
    public VoiceCallTrace trace() {
        return trace;
    }

    private void execute(@NonNull VoiceCallEffect effect) {
        if (effect instanceof VoiceCallEffect.Publish) {
            publish();
        } else if (effect instanceof VoiceCallEffect.StartCapture) {
            capture.start(INPUT_SAMPLE_RATE_HZ, captureListener);
            apply(new VoiceCallEvent.DuplexResolved(duplex));
        } else if (effect instanceof VoiceCallEffect.StopCapture) {
            bestEffort(capture::stop);
        } else if (effect instanceof VoiceCallEffect.SetUplinkEnabled gate) {
            bestEffort(() -> capture.setUplinkEnabled(gate.enabled()));
        } else if (effect instanceof VoiceCallEffect.StartPlayback start) {
            cancelDrain();
            try {
                playback.start(start.sampleRateHz());
            } catch (RuntimeException broken) {
                bestEffort(() -> apply(new VoiceCallEvent.Failed(new AiFailure(
                        AiFailureKind.UNKNOWN, detailOf(broken), broken))));
            }
        } else if (effect instanceof VoiceCallEffect.PlayAudio audio) {
            try {
                playback.write(audio.pcm16(), audio.itemId(), audio.contentIndex());
            } catch (RuntimeException ignored) {
                // 单块写失败是播放侧抖动，不让整通电话失败。
            }
        } else if (effect instanceof VoiceCallEffect.StopAssistant stop) {
            stopAssistant(stop.cancelGeneration());
        } else if (effect instanceof VoiceCallEffect.ScheduleDrain drain) {
            cancelDrain();
            drainFuture = drainScheduler.schedule(
                    () -> dispatch(() -> apply(new VoiceCallEvent.PlaybackDrained())),
                    Math.max(0L, drain.delayMillis()), TimeUnit.MILLISECONDS);
        } else if (effect instanceof VoiceCallEffect.StopPlayback) {
            cancelDrain();
            bestEffort(playback::stop);
        } else if (effect instanceof VoiceCallEffect.CloseLink) {
            bestEffort(link::close);
        }
    }

    /**
     * 停止助手发言的固定顺序：先取进度 → 清队列 → 取消/截断 → 丢上行缓冲。
     *
     * <p>先取进度再清队列，是因为"用户听到多少"只存在于播放端口的记账里；
     * 反过来清完再取，就只剩 0 毫秒，服务端会以为用户什么都没听到，
     * 下一轮回答又会把整段重说一遍。</p>
     */
    private void stopAssistant(boolean cancelGeneration) {
        AiAudioPlayback.Heard heard = null;
        try {
            heard = playback.captureHeard();
        } catch (RuntimeException ignored) {
            // 取不到进度就退化成"不截断"，比拿一个错的毫秒数去截断安全。
        }
        bestEffort(playback::flush);
        bestEffort(() -> link.send(new AiRealtimeInput.Clear()));
        if (cancelGeneration) {
            bestEffort(() -> link.send(new AiRealtimeInput.Cancel()));
        }
        if (heard != null && heard.itemId() != null && !heard.itemId().isBlank()
                && heard.elapsedMs() > 0) {
            final String itemId = heard.itemId();
            final int contentIndex = heard.contentIndex();
            final int audioEndMs = heard.elapsedMs();
            bestEffort(() -> link.truncate(itemId, contentIndex, audioEndMs));
        }
    }

    private void cancelDrain() {
        ScheduledFuture<?> current = drainFuture;
        drainFuture = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    // ---- 对外快照 ----

    private void publish() {
        if (silenced) {
            return;
        }
        Listener target = listener;
        if (target == null) {
            return;
        }
        AiVoiceSnapshot snapshot = new AiVoiceSnapshot(
                snapshotState(model.state()), model.turns(), model.level(), model.failure(),
                model.duplex());
        try {
            target.onSnapshot(snapshot);
        } catch (RuntimeException fromPage) {
            // 页面回调是外部代码：它抛错属于界面缺陷，不该把状态机带停。
        }
    }

    @NonNull
    private static AiVoiceSnapshot.State snapshotState(@NonNull VoiceCallState state) {
        return switch (state) {
            case IDLE, CONNECTING -> AiVoiceSnapshot.State.CONNECTING;
            case LISTENING -> AiVoiceSnapshot.State.LISTENING;
            case THINKING -> AiVoiceSnapshot.State.THINKING;
            case SPEAKING, DRAINING -> AiVoiceSnapshot.State.SPEAKING;
            case MUTED -> AiVoiceSnapshot.State.MUTED;
            case FAILED -> AiVoiceSnapshot.State.FAILED;
            case ENDED -> AiVoiceSnapshot.State.ENDED;
        };
    }

    // ---- 连接目标 ----

    @NonNull
    private RealtimePlan planFor() {
        AiSettings current;
        try {
            current = settings.load();
        } catch (RuntimeException unreadable) {
            throw new AiFailure(AiFailureKind.STORAGE, null, unreadable);
        }
        AiVoiceConfig voice = current.voice();
        String apiKey = credentials.voiceKey(voice.model());
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiFailure(AiFailureKind.VOICE_NOT_CONFIGURED, null);
        }
        return new RealtimePlan(voice.effectiveBaseUrl(), apiKey, voice.resolvedRealtimeModel());
    }

    @NonNull
    private static ScheduledExecutorService newScheduler() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "ai-voice-drain");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void bestEffort(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException alreadyReported) {
            // 释放路径一步失败不能挡住其它步骤。
        }
    }

    @NonNull
    private static String detailOf(Throwable error) {
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail;
    }

    private record RealtimePlan(String baseUrl, String apiKey, String model) {
    }
}
