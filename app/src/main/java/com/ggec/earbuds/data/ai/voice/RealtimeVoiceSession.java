package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.port.AiAudioPlayback;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.port.AiAudioSourceCatalog;
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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 语音通话 actor：串行处理 {@link VoiceCallEvent}，调用纯 {@link VoiceCallReducer}
 * 计算下一状态与副作用，再按顺序调度 {@link AudioUplinkPipeline}、{@link AiAudioPlayback} 与 {@link AiRealtimeLink}。
 *
 * <p>三条硬约定，缺一条就会在真机上出现"状态乱跳 / 事件凭空消失"：</p>
 * <ol>
 *   <li><b>effect 批次不重入</b>：任何一步 effect 失败都只把「分类后的失败事件重新入队」并丢弃本批剩余 effect，
 *       绝不在 effect 循环里就地 {@code apply(Failed)}——否则状态机会在半个批次上二次迁移；</li>
 *   <li><b>没有前置标志位</b>：能不能开始、能不能发布，只由状态机的显式终态（{@code FAILED}/{@code ENDED}）
 *       与 {@code listener} 是否存在裁决，不用布尔哨兵提前拦截事件；</li>
 *   <li><b>失败必须落痕</b>：清理期、调度期无法再进状态机的异常，一律按分类写进 {@link VoiceCallTrace#failures()}，
 *       不留空 catch。</li>
 * </ol>
 *
 * <p>音频源（内麦、蓝牙耳机、USB 外设或外部推流）的发现与切换全部通过 {@link AiAudioSourceCatalog}
 * 与 {@link AudioUplinkPipeline} 热插拔闭环完成，切换输入源对网络链路和会话上下文完全透明。</p>
 */
public final class RealtimeVoiceSession implements AiVoiceSession {

    /** THINKING 等不到服务端回答的容忍上限；超时由本类调度器投递 {@link VoiceCallEvent.Timeout}。 */
    static final long THINKING_TIMEOUT_MS = 15_000L;

    private final AiSettingsRepository settings;
    private final AiCredentialStore credentials;
    private final AiAudioSourceCatalog sourceCatalog;
    private final AiAudioPlayback playback;
    private final AiRealtimeLink link;
    private final AiClock clock;
    /** 所有状态字段只在它的线程上读写。 */
    private final Executor executor;
    /**
     * 播放排空延时与 THINKING 超时的定时器。由装配根注入，本会话不拥有线程池：
     * 挂断时只取消自己排下的任务，线程池随进程回收。
     */
    private final ScheduledExecutorService timer;
    private final long thinkingTimeoutMillis;
    private final VoiceCallTrace trace = new VoiceCallTrace();

    private final AudioUplinkPipeline uplink;
    private final AiAudioSourceCatalog.Observer catalogObserver;
    private final AiRealtimeLink.Listener linkListener;

    // ---- 只在 executor 线程上读写 ----
    @Nullable
    private Listener listener;
    private boolean duplexPinned;
    private boolean uplinkActive;
    private boolean observingCatalog;
    @Nullable
    private AiAudioSource selectedSource;
    private boolean selectedFromCatalog;
    private VoiceCallReducer.Model model;
    @Nullable
    private ScheduledFuture<?> drainFuture;
    @Nullable
    private ScheduledFuture<?> thinkingFuture;

    public RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiCredentialStore credentials,
            @NonNull AiAudioSourceCatalog sourceCatalog,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor,
            @NonNull ScheduledExecutorService timer) {
        this(settings, credentials, sourceCatalog, playback, link, clock, executor, timer,
                null, THINKING_TIMEOUT_MS);
    }

    public RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiCredentialStore credentials,
            @NonNull AiAudioSourceCatalog sourceCatalog,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor,
            @NonNull ScheduledExecutorService timer,
            @NonNull VoiceDuplex duplex) {
        this(settings, credentials, sourceCatalog, playback, link, clock, executor, timer,
                Objects.requireNonNull(duplex, "duplex"), THINKING_TIMEOUT_MS);
    }

    /** 单测可直接用它把 THINKING 超时压到毫秒级；生产只走上面两个构造器。 */
    RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiCredentialStore credentials,
            @NonNull AiAudioSourceCatalog sourceCatalog,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor,
            @NonNull ScheduledExecutorService timer,
            @Nullable VoiceDuplex explicitDuplex,
            long thinkingTimeoutMillis) {
        if (thinkingTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("thinkingTimeoutMillis must be positive");
        }
        this.settings = Objects.requireNonNull(settings, "settings");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.sourceCatalog = Objects.requireNonNull(sourceCatalog, "sourceCatalog");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.link = Objects.requireNonNull(link, "link");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.timer = Objects.requireNonNull(timer, "timer");
        this.thinkingTimeoutMillis = thinkingTimeoutMillis;
        this.duplexPinned = explicitDuplex != null;
        this.model = VoiceCallReducer.Model.initial(
                explicitDuplex != null ? explicitDuplex : VoiceDuplex.defaultMode());
        this.uplink = new AudioUplinkPipeline(
                link,
                clock,
                level -> dispatch(() -> apply(new VoiceCallEvent.CaptureLevel(level))),
                (kind, detail) -> dispatch(() -> apply(new VoiceCallEvent.Failed(
                        new AiFailure(kind, detail.isEmpty() ? null : detail)))));
        this.catalogObserver = sources -> dispatch(() -> onAvailableSourcesChanged(sources));
        this.linkListener = output -> dispatch(() -> onLinkOutput(output));
    }

    /**
     * 所有入队入口。执行器已关停时机器无法再推进，把分类写进轨迹而不是空 catch。
     */
    private void dispatch(@NonNull Runnable work) {
        try {
            executor.execute(work);
        } catch (RuntimeException rejected) {
            recordFailure("dispatch", rejected);
        }
    }

    // ---- AiVoiceSession ----

    @Override
    public void start(@NonNull Listener target) {
        Objects.requireNonNull(target, "listener");
        dispatch(() -> doStart(target));
    }

    @Override
    public void setMicMuted(boolean value) {
        dispatch(() -> apply(new VoiceCallEvent.SetMicMuted(value)));
    }

    @Override
    public void setDuplex(@NonNull VoiceDuplex duplex) {
        Objects.requireNonNull(duplex, "duplex");
        dispatch(() -> {
            duplexPinned = true;
            apply(new VoiceCallEvent.SetDuplex(duplex));
        });
    }

    @Override
    public void selectAudioSource(@NonNull String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        dispatch(() -> {
            if (model.terminal()) {
                return;
            }
            AiAudioSource resolved = sourceCatalog.resolve(sourceId);
            if (resolved != null) {
                switchToSource(resolved, true);
            }
        });
    }

    @Override
    public void setAudioSource(@NonNull AiAudioSource source) {
        Objects.requireNonNull(source, "source");
        dispatch(() -> {
            if (model.terminal()) {
                return;
            }
            switchToSource(source, false);
        });
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
        dispatch(this::doStop);
    }

    // ---- 生命周期 ----

    private void doStart(Listener target) {
        // 唯一决定"还能不能开始"的是显式终态与是否已经有人接管回调，没有前置标志位。
        if (listener != null || model.terminal()) {
            return;
        }
        listener = target;
        if (!observingCatalog) {
            sourceCatalog.addObserver(catalogObserver);
            observingCatalog = true;
        }
        model = model.withAvailableSources(sourceCatalog.availableSources());
        if (selectedSource == null) {
            AiAudioSource def = sourceCatalog.defaultSource();
            if (def != null) {
                selectedSource = def;
                selectedFromCatalog = true;
            }
        }
        if (selectedSource != null) {
            model = model.withActiveSource(selectedSource.descriptor());
        }

        RealtimePlan plan;
        try {
            plan = planFor();
        } catch (AiFailure unusable) {
            apply(new VoiceCallEvent.Failed(unusable));
            return;
        }
        if (!duplexPinned && plan.defaultDuplex() != model.duplex()) {
            model = model.withDuplex(plan.defaultDuplex());
        }
        apply(new VoiceCallEvent.Start());
        try {
            link.open(new AiRealtimeLink.Config(plan.baseUrl(), plan.apiKey(), plan.model()),
                    linkListener);
        } catch (RuntimeException broken) {
            apply(new VoiceCallEvent.Failed(classify(broken)));
        }
    }

    private void doStop() {
        apply(new VoiceCallEvent.Stop());
        stopCatalogObservation();
        listener = null;
        // 挂断即收回本会话排下的全部定时：定时器是装配根的，会话只取消自己的任务。
        cancelThinkingTimeout();
        cancelDrain();
    }

    private void onAvailableSourcesChanged(@NonNull List<AiAudioSource.Descriptor> sources) {
        if (model.terminal()) {
            return;
        }
        apply(new VoiceCallEvent.AvailableSourcesChanged(sources));
        // 若当前使用的是目录源，且该外设在通话途中被移除（例如蓝牙耳机断开），自动回退到当前默认源无缝续传。
        if (selectedSource != null && selectedFromCatalog) {
            AiAudioSource stillPresent = sourceCatalog.resolve(selectedSource.descriptor().id());
            if (stillPresent == null) {
                AiAudioSource defaultSource = sourceCatalog.defaultSource();
                if (defaultSource != null) {
                    switchToSource(defaultSource, true);
                } else {
                    selectedSource = null;
                    apply(new VoiceCallEvent.SetActiveSource(null));
                }
            }
        }
    }

    private void switchToSource(@NonNull AiAudioSource source, boolean fromCatalog) {
        selectedSource = source;
        selectedFromCatalog = fromCatalog;
        apply(new VoiceCallEvent.SetActiveSource(source.descriptor()));
        if (uplinkActive) {
            uplink.switchSource(source);
        }
    }

    private void onLinkOutput(@NonNull AiRealtimeOutput output) {
        switch (output) {
            case AiRealtimeOutput.Opened opened ->
                    apply(new VoiceCallEvent.Opened(opened.inputSampleRateHz()));
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

    // ---- actor ----

    private void apply(@NonNull VoiceCallEvent event) {
        long now = clock.now();
        VoiceCallState before = model.state();
        VoiceCallReducer.Transition transition = VoiceCallReducer.reduce(model, event, now);
        model = transition.model();
        if (before != model.state() || !transition.effects().isEmpty()) {
            trace.record(now, before, event, model.state());
        }
        syncThinkingDeadline(before);
        runEffects(transition.effects());
    }

    /** 供测试与现场排障导出：最近的状态转移轨迹与分类失败。 */
    @NonNull
    public VoiceCallTrace trace() {
        return trace;
    }

    /**
     * THINKING 只在进入时排一次超时、离开时撤销：同一个 THINKING 不会漏判或多判，
     * 上一轮的超时也不会串到下一轮。
     */
    private void syncThinkingDeadline(@NonNull VoiceCallState before) {
        if (before == VoiceCallState.THINKING && model.state() != VoiceCallState.THINKING) {
            cancelThinkingTimeout();
        } else if (before != VoiceCallState.THINKING && model.state() == VoiceCallState.THINKING) {
            scheduleThinkingTimeout();
        }
    }

    private void scheduleThinkingTimeout() {
        cancelThinkingTimeout();
        try {
            thinkingFuture = timer.schedule(
                    () -> dispatch(() -> apply(new VoiceCallEvent.Timeout())),
                    thinkingTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException rejected) {
            recordFailure("scheduleThinkingTimeout", rejected);
        }
    }

    private void cancelThinkingTimeout() {
        ScheduledFuture<?> current = thinkingFuture;
        thinkingFuture = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    /**
     * 按序执行一批 effect。任一步失败只把「分类后的失败事件重新入队」并丢弃本批剩余 effect，
     * 状态机因此只会从完整批次边界上迁移一次。
     */
    private void runEffects(@NonNull List<VoiceCallEffect> effects) {
        for (VoiceCallEffect effect : effects) {
            AiFailure failure = execute(effect);
            if (failure != null) {
                failBatch(effect, failure);
                return;
            }
        }
    }

    /**
     * 执行单个 effect。
     *
     * @return {@code null} 表示这一步做成了；否则返回分类后的失败，由 {@link #runEffects} 收口。
     */
    @Nullable
    private AiFailure execute(@NonNull VoiceCallEffect effect) {
        try {
            if (effect instanceof VoiceCallEffect.Publish) {
                publish();
            } else if (effect instanceof VoiceCallEffect.StartUplink start) {
                AiAudioSource source = selectedSource;
                if (source == null) {
                    return new AiFailure(AiFailureKind.UNKNOWN, "No available audio input source");
                }
                uplink.start(source, start.wireSampleRateHz());
                uplinkActive = true;
            } else if (effect instanceof VoiceCallEffect.StopUplink) {
                uplinkActive = false;
                uplink.stop();
                if (model.terminal()) {
                    stopCatalogObservation();
                }
            } else if (effect instanceof VoiceCallEffect.SetUplinkEnabled gate) {
                uplink.setEnabled(gate.enabled());
            } else if (effect instanceof VoiceCallEffect.StartPlayback start) {
                cancelDrain();
                playback.start(start.sampleRateHz());
            } else if (effect instanceof VoiceCallEffect.PlayAudio audio) {
                playback.write(audio.pcm16(), audio.itemId(), audio.contentIndex());
            } else if (effect instanceof VoiceCallEffect.StopAssistant stop) {
                stopAssistant(stop.cancelGeneration());
            } else if (effect instanceof VoiceCallEffect.ScheduleDrain drain) {
                cancelDrain();
                drainFuture = timer.schedule(
                        () -> dispatch(() -> apply(new VoiceCallEvent.PlaybackDrained())),
                        Math.max(0L, drain.delayMillis()), TimeUnit.MILLISECONDS);
            } else if (effect instanceof VoiceCallEffect.StopPlayback) {
                cancelDrain();
                playback.stop();
            } else if (effect instanceof VoiceCallEffect.CloseLink) {
                link.close();
            }
            return null;
        } catch (RuntimeException broken) {
            return classify(broken);
        }
    }

    private void failBatch(@NonNull VoiceCallEffect effect, @NonNull AiFailure failure) {
        recordFailure(effect.getClass().getSimpleName(), failure);
        dispatch(() -> apply(new VoiceCallEvent.Failed(failure)));
    }

    private void stopAssistant(boolean cancelGeneration) {
        AiAudioPlayback.Heard heard = captureHeard();
        flushPlayback();
        sendLink(new AiRealtimeInput.Clear());
        if (cancelGeneration) {
            sendLink(new AiRealtimeInput.Cancel());
        }
        if (heard != null && heard.itemId() != null && !heard.itemId().isBlank()
                && heard.elapsedMs() > 0) {
            sendLink(new AiRealtimeInput.Truncate(
                    heard.itemId(), heard.contentIndex(), heard.elapsedMs()));
        }
    }

    @Nullable
    private AiAudioPlayback.Heard captureHeard() {
        try {
            return playback.captureHeard();
        } catch (RuntimeException broken) {
            recordFailure("playback.captureHeard", broken);
            return null;
        }
    }

    private void flushPlayback() {
        try {
            playback.flush();
        } catch (RuntimeException broken) {
            recordFailure("playback.flush", broken);
        }
    }

    private void sendLink(@NonNull AiRealtimeInput input) {
        try {
            link.send(input);
        } catch (RuntimeException broken) {
            recordFailure("link." + input.getClass().getSimpleName(), broken);
        }
    }

    private void cancelDrain() {
        ScheduledFuture<?> current = drainFuture;
        drainFuture = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private void stopCatalogObservation() {
        if (observingCatalog) {
            observingCatalog = false;
            try {
                sourceCatalog.removeObserver(catalogObserver);
            } catch (RuntimeException broken) {
                recordFailure("catalog.removeObserver", broken);
            }
        }
    }

    // ---- 分类上报 ----

    private void recordFailure(@NonNull String site, @NonNull Throwable error) {
        recordFailure(site, classify(error));
    }

    private void recordFailure(@NonNull String site, @NonNull AiFailure failure) {
        trace.recordFailure(clock.now(), site, failure);
    }

    @NonNull
    private static AiFailure classify(@NonNull Throwable error) {
        if (error instanceof AiFailure domainFailure) {
            return domainFailure;
        }
        AiFailureKind kind = error instanceof RejectedExecutionException
                ? AiFailureKind.CANCELLED
                : AiFailureKind.UNKNOWN;
        return new AiFailure(kind, detailOf(error), error);
    }

    // ---- 对外快照 ----

    private void publish() {
        Listener target = listener;
        if (target == null) {
            return;
        }
        AiVoiceSnapshot snapshot = new AiVoiceSnapshot(
                snapshotState(model.state()),
                model.turns(),
                model.level(),
                model.failure(),
                model.duplex(),
                model.micMuted(),
                model.activeSource(),
                model.availableSources()
        );
        try {
            target.onSnapshot(snapshot);
        } catch (RuntimeException fromPage) {
            // 页面回调自己抛异常不能反过来打断通话，但必须留痕。
            recordFailure("listener.onSnapshot", fromPage);
        }
    }

    @NonNull
    private static AiVoiceSnapshot.State snapshotState(@NonNull VoiceCallState state) {
        return switch (state) {
            case IDLE, CONNECTING -> AiVoiceSnapshot.State.CONNECTING;
            case LISTENING -> AiVoiceSnapshot.State.LISTENING;
            case THINKING -> AiVoiceSnapshot.State.THINKING;
            case SPEAKING, DRAINING -> AiVoiceSnapshot.State.SPEAKING;
            case FAILED -> AiVoiceSnapshot.State.FAILED;
            case ENDED -> AiVoiceSnapshot.State.ENDED;
        };
    }

    // ---- 连接目标 ----

    @NonNull
    private RealtimePlan planFor() {
        // 读不出来时 load() 自己就抛 AI 失败（STORAGE + 文档坐标），这里不再包一层。
        AiSettings current = settings.load();
        AiVoiceConfig voice = current.voice();
        String apiKey = credentials.voiceKey(voice.model());
        if (apiKey == null || apiKey.isBlank()) {
            // 载荷只有分类：界面按 kind 出文案，这里不放英文说明。
            throw new AiFailure(AiFailureKind.VOICE_NOT_CONFIGURED, null);
        }
        return new RealtimePlan(voice.effectiveBaseUrl(), apiKey, voice.resolvedRealtimeModel(),
                voice.defaultDuplex());
    }

    @NonNull
    private static String detailOf(Throwable error) {
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail;
    }

    private record RealtimePlan(String baseUrl, String apiKey, String model, VoiceDuplex defaultDuplex) {
    }
}
