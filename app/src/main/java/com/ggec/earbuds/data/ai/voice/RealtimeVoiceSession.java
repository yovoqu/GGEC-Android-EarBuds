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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 语音通话 actor：串行处理 {@link VoiceCallEvent}，调用纯 {@link VoiceCallReducer}
 * 计算下一状态与副作用，再按顺序调度 {@link AudioUplinkPipeline}、{@link AiAudioPlayback} 与 {@link AiRealtimeLink}。
 *
 * <p>音频源（内麦、蓝牙耳机、USB 外设或外部推流）的发现与切换全部通过 {@link AiAudioSourceCatalog}
 * 与 {@link AudioUplinkPipeline} 热插拔闭环完成，切换输入源对网络链路和会话上下文完全透明。</p>
 */
public final class RealtimeVoiceSession implements AiVoiceSession {

    private final AiSettingsRepository settings;
    private final AiCredentialStore credentials;
    private final AiAudioSourceCatalog sourceCatalog;
    private final AiAudioPlayback playback;
    private final AiRealtimeLink link;
    private final AiClock clock;
    /** 所有状态字段只在它的线程上读写。 */
    private final Executor executor;
    private final ScheduledExecutorService drainScheduler;
    private final VoiceCallTrace trace = new VoiceCallTrace();

    private final AudioUplinkPipeline uplink;
    private final AiAudioSourceCatalog.Observer catalogObserver;
    private final AiRealtimeLink.Listener linkListener;

    /** 挂断从任意线程发起，必须在 start 派发之前就生效。 */
    private volatile boolean stopRequested;

    // ---- 只在 executor 线程上读写 ----
    @Nullable
    private Listener listener;
    private boolean silenced;
    private boolean duplexPinned;
    private boolean uplinkActive;
    private boolean observingCatalog;
    @Nullable
    private AiAudioSource selectedSource;
    private boolean selectedFromCatalog;
    private VoiceCallReducer.Model model;
    @Nullable
    private ScheduledFuture<?> drainFuture;

    public RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiCredentialStore credentials,
            @NonNull AiAudioSourceCatalog sourceCatalog,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor) {
        this(settings, credentials, sourceCatalog, playback, link, clock, executor,
                null, newScheduler());
    }

    public RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiCredentialStore credentials,
            @NonNull AiAudioSourceCatalog sourceCatalog,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor,
            @NonNull VoiceDuplex duplex) {
        this(settings, credentials, sourceCatalog, playback, link, clock, executor,
                Objects.requireNonNull(duplex, "duplex"), newScheduler());
    }

    private RealtimeVoiceSession(
            @NonNull AiSettingsRepository settings,
            @NonNull AiCredentialStore credentials,
            @NonNull AiAudioSourceCatalog sourceCatalog,
            @NonNull AiAudioPlayback playback,
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull Executor executor,
            @Nullable VoiceDuplex explicitDuplex,
            @NonNull ScheduledExecutorService drainScheduler) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.sourceCatalog = Objects.requireNonNull(sourceCatalog, "sourceCatalog");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.link = Objects.requireNonNull(link, "link");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.drainScheduler = Objects.requireNonNull(drainScheduler, "drainScheduler");
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

    private void dispatch(@NonNull Runnable work) {
        if (stopRequested) {
            return;
        }
        try {
            executor.execute(work);
        } catch (RuntimeException shuttingDown) {
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
        stopRequested = true;
        try {
            executor.execute(this::doStop);
        } catch (RuntimeException shuttingDown) {
        }
    }

    // ---- 生命周期 ----

    private void doStart(Listener target) {
        if (stopRequested || listener != null || model.terminal()) {
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
            apply(new VoiceCallEvent.Failed(new AiFailure(AiFailureKind.UNKNOWN,
                    detailOf(broken), broken)));
        }
    }

    private void doStop() {
        apply(new VoiceCallEvent.Stop());
        stopCatalogObservation();
        silenced = true;
        listener = null;
        drainScheduler.shutdownNow();
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
                AiAudioSource fallback = sourceCatalog.defaultSource();
                if (fallback != null) {
                    switchToSource(fallback, true);
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
        } else if (effect instanceof VoiceCallEffect.StartUplink start) {
            AiAudioSource source = selectedSource;
            if (source == null) {
                source = sourceCatalog.defaultSource();
                if (source != null) {
                    selectedSource = source;
                    selectedFromCatalog = true;
                    model = model.withActiveSource(source.descriptor());
                }
            }
            if (source == null) {
                apply(new VoiceCallEvent.Failed(new AiFailure(
                        AiFailureKind.UNKNOWN, "No available audio input source")));
                return;
            }
            uplinkActive = true;
            uplink.start(source, start.wireSampleRateHz());
        } else if (effect instanceof VoiceCallEffect.StopUplink) {
            uplinkActive = false;
            bestEffort(uplink::stop);
            if (model.terminal()) {
                stopCatalogObservation();
            }
        } else if (effect instanceof VoiceCallEffect.SetUplinkEnabled gate) {
            uplink.setEnabled(gate.enabled());
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

    private void stopAssistant(boolean cancelGeneration) {
        AiAudioPlayback.Heard heard = null;
        try {
            heard = playback.captureHeard();
        } catch (RuntimeException ignored) {
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
            bestEffort(() -> link.send(new AiRealtimeInput.Truncate(itemId, contentIndex, audioEndMs)));
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
            bestEffort(() -> sourceCatalog.removeObserver(catalogObserver));
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
        return new RealtimePlan(voice.effectiveBaseUrl(), apiKey, voice.resolvedRealtimeModel(),
                voice.defaultDuplex());
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
        }
    }

    @NonNull
    private static String detailOf(Throwable error) {
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail;
    }

    private record RealtimePlan(String baseUrl, String apiKey, String model, VoiceDuplex defaultDuplex) {
    }
}
