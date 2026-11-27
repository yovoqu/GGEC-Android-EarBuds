package com.ggec.earbuds.ui.device.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.PlaybackOutput;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.RecordOutcome;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;
import com.ggec.earbuds.domain.device.model.SyncProgressState;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.PcmPlaybackPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.record.RecordEffect;
import com.ggec.earbuds.domain.device.record.RecordEvent;
import com.ggec.earbuds.domain.device.record.RecordFailure;
import com.ggec.earbuds.domain.device.record.RecordMachine;
import com.ggec.earbuds.domain.device.record.RecordState;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 录音功能的 ViewModel：只做「意图 → 事件 → 状态机 → 效果执行 + 状态发布」。
 *
 * <p>本类不持业务判定、不持可变业务字段、不读时钟、不拼用户文案：</p>
 * <ul>
 *   <li>所有业务决策在 {@link RecordMachine}（domain，纯函数）；</li>
 *   <li>所有渲染字段由 {@link RecordUiMapper} 从 {@link RecordState} 派生；</li>
 *   <li>计时用注入的 {@link LongSupplier}（真实时间）与 {@link Ticker}（调度），
 *       当前时间只作为事件载荷进入状态机；</li>
 *   <li>要给用户看的提示只以 {@link RecordEffect.Notice} 分类上抛，由 Activity 映射到
 *       {@code strings.xml}。</li>
 * </ul>
 */
public final class RecordViewModel extends ViewModel {

    /** 调度抽象：平台实现（Handler）留在装配层。 */
    public interface Ticker {
        void postDelayed(@NonNull Runnable runnable, long delayMs);

        void removeCallbacks(@NonNull Runnable runnable);
    }

    /** 耳机本地录音计时的节拍。 */
    private static final long TICK_INTERVAL_MS = 1000L;

    /** 本地录音播放进度的刷新节拍：足够顺滑，又不至于每帧重建列表。 */
    private static final long PLAYBACK_TICK_MS = 500L;

    private final RecordPort recordPort;
    private final ObserveDevice observeDevice;
    private final PcmPlaybackPort playbackPort;
    private final Ticker ticker;
    private final LongSupplier clock;
    @Nullable
    private final DeviceCapabilityCatalog catalog;
    private final RecordUiMapper mapper = new RecordUiMapper();

    private final MutableLiveData<RecordUiState> uiState;
    private final OneShotLiveData<RecordEffect.Notice> notices = new OneShotLiveData<>();

    private final Subscription deviceSubscription;
    private final RecordPort.StreamRecordObserver streamObserver = new PortStreamObserver();

    private RecordState state = RecordState.initial();
    private volatile boolean activeDuplexSupported;
    private boolean deviceTicking;
    private boolean playbackTicking;

    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            synchronized (RecordViewModel.this) {
                deviceTicking = false;
            }
            dispatch(new RecordEvent.DeviceRecordTicked(clock.getAsLong()));
        }
    };

    private final Runnable playbackTickTask = new Runnable() {
        @Override
        public void run() {
            synchronized (RecordViewModel.this) {
                playbackTicking = false;
            }
            dispatch(new RecordEvent.PlaybackProgress(
                    playbackPort.positionMs(), playbackPort.durationMs()));
        }
    };

    public RecordViewModel(@NonNull RecordPort recordPort,
                           @NonNull ObserveDevice observeDevice,
                           @NonNull PcmPlaybackPort playbackPort,
                           @NonNull Ticker ticker,
                           @NonNull LongSupplier clock) {
        this(recordPort, observeDevice, playbackPort, ticker, clock, null);
    }

    public RecordViewModel(@NonNull RecordPort recordPort,
                           @NonNull ObserveDevice observeDevice,
                           @NonNull PcmPlaybackPort playbackPort,
                           @NonNull Ticker ticker,
                           @NonNull LongSupplier clock,
                           @Nullable DeviceCapabilityCatalog catalog) {
        this.recordPort = Objects.requireNonNull(recordPort, "recordPort");
        this.observeDevice = Objects.requireNonNull(observeDevice, "observeDevice");
        this.playbackPort = Objects.requireNonNull(playbackPort, "playbackPort");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.catalog = catalog;
        this.uiState = new MutableLiveData<>(mapper.map(state));

        this.deviceSubscription = observeDevice.execute(this::onDeviceSnapshot);
        onDeviceSnapshot(observeDevice.current());
        dispatch(new RecordEvent.LocalFilesReloadRequested());
    }

    @NonNull
    public LiveData<RecordUiState> uiState() {
        return uiState;
    }

    /** 一次性通知分类：观察者翻成文案后立即清空，旋转不重放。 */
    @NonNull
    public LiveData<RecordEffect.Notice> notices() {
        return notices;
    }

    // ==================== 意图转发 ====================

    public void setMode(@NonNull RecordMode mode) {
        dispatch(new RecordEvent.ModeSelected(mode));
    }

    public void startStreamRecord() {
        dispatch(new RecordEvent.StreamStartRequested());
    }

    public void stopStreamRecord() {
        dispatch(new RecordEvent.StreamStopRequested());
    }

    public void startDeviceRecord() {
        dispatch(new RecordEvent.DeviceRecordStartRequested());
    }

    public void stopDeviceRecord() {
        dispatch(new RecordEvent.DeviceRecordStopRequested());
    }

    public void refreshDeviceRecords() {
        dispatch(new RecordEvent.RefreshRequested());
    }

    public void syncDeviceRecord(@NonNull String fileId) {
        dispatch(new RecordEvent.SyncRequested(fileId));
    }

    public void cancelSync() {
        dispatch(new RecordEvent.SyncCancelRequested());
    }

    public void deleteDeviceRecord(@NonNull String fileId) {
        dispatch(new RecordEvent.DeviceDeleteRequested(fileId));
    }

    public void reloadLocalFiles() {
        dispatch(new RecordEvent.LocalFilesReloadRequested());
    }

    public void togglePlayLocalFile(@NonNull SavedRecordItem item) {
        dispatch(new RecordEvent.PlayRequested(item));
    }

    public void stopPlayback() {
        dispatch(new RecordEvent.PlaybackStopRequested());
    }

    /** 切换正在播放的物理输出设备（播放源）。 */
    public void selectPlaybackOutput(@NonNull String outputId) {
        dispatch(new RecordEvent.PlaybackOutputSelectRequested(outputId));
    }

    public void deleteLocalFile(@NonNull SavedRecordItem item) {
        dispatch(new RecordEvent.LocalDeleteRequested(item));
    }

    // ==================== 状态机驱动 ====================

    private void dispatch(@NonNull RecordEvent event) {
        RecordMachine.Transition transition;
        boolean wasRecording;
        boolean nowRecording;
        boolean playbackActive;
        synchronized (this) {
            wasRecording = isRecordingActiveLocked();
            transition = RecordMachine.reduce(state, event);
            state = transition.state();
            nowRecording = isRecordingActiveLocked();
            playbackActive = state.playback().active();
            uiState.postValue(mapper.map(state));
        }
        for (RecordEffect effect : transition.effects()) {
            execute(effect);
        }
        if (playbackActive && wasRecording != nowRecording && !(event instanceof RecordEvent.PlaybackRouteChanged)) {
            publishPlaybackRoute();
        }
        syncTicker();
    }

    private synchronized void syncTicker() {
        boolean deviceActive = state.deviceRecord().phase() == RecordState.DeviceRecordPhase.ACTIVE;
        if (deviceActive != deviceTicking) {
            deviceTicking = deviceActive;
            if (deviceActive) {
                ticker.postDelayed(tickTask, TICK_INTERVAL_MS);
            } else {
                ticker.removeCallbacks(tickTask);
            }
        }

        boolean playbackActive = state.playback().active();
        if (playbackActive != playbackTicking) {
            playbackTicking = playbackActive;
            if (playbackActive) {
                ticker.postDelayed(playbackTickTask, PLAYBACK_TICK_MS);
            } else {
                ticker.removeCallbacks(playbackTickTask);
            }
        }
    }

    private void execute(@NonNull RecordEffect effect) {
        switch (effect) {
            case RecordEffect.StartStream ignored -> recordPort.startStreamRecord(streamObserver);
            case RecordEffect.StopStream ignored -> recordPort.stopStreamRecord(outcome -> onOutcome(outcome,
                    () -> dispatch(new RecordEvent.StreamStopSucceeded()),
                    failure -> dispatch(new RecordEvent.StreamStopFailed(failure))));
            case RecordEffect.StartDeviceRecord ignored -> recordPort.startDeviceRecord(outcome -> onOutcome(
                    outcome,
                    () -> dispatch(new RecordEvent.DeviceRecordStarted(clock.getAsLong())),
                    failure -> dispatch(new RecordEvent.DeviceRecordStartFailed(failure))));
            case RecordEffect.StopDeviceRecord ignored -> recordPort.stopDeviceRecord(outcome -> onOutcome(
                    outcome,
                    () -> dispatch(new RecordEvent.DeviceRecordStopped()),
                    failure -> dispatch(new RecordEvent.DeviceRecordStopFailed(failure))));
            case RecordEffect.RefreshDeviceFiles ignored -> recordPort.refreshDeviceRecords(outcome ->
                    onOutcome(outcome,
                            () -> dispatch(new RecordEvent.RefreshSucceeded()),
                            failure -> dispatch(new RecordEvent.RefreshFailed(failure))));
            case RecordEffect.SyncFile sync ->
                    recordPort.syncDeviceRecord(sync.fileId(), new PortSyncObserver(sync.fileId()));
            case RecordEffect.CancelSync ignored -> recordPort.cancelSyncDeviceRecord(outcome ->
                    onOutcome(outcome,
                            () -> dispatch(new RecordEvent.SyncCancelSucceeded()),
                            failure -> dispatch(new RecordEvent.SyncCancelFailed(failure))));
            case RecordEffect.DeleteDeviceFile delete ->
                    recordPort.deleteDeviceRecord(delete.fileId(), outcome -> onOutcome(outcome,
                            () -> dispatch(new RecordEvent.DeviceDeleteSucceeded(delete.fileId())),
                            failure -> dispatch(new RecordEvent.DeviceDeleteFailed(
                                    delete.fileId(), failure))));
            case RecordEffect.DeleteLocalFile delete -> onOutcome(
                    recordPort.deleteLocalSavedFile(delete.filePath()),
                    () -> dispatch(new RecordEvent.LocalDeleteSucceeded(delete.filePath())),
                    failure -> dispatch(new RecordEvent.LocalDeleteFailed(
                            delete.filePath(), failure)));
            case RecordEffect.Play play -> {
                playbackPort.play(play.filePath(), () -> dispatch(new RecordEvent.PlaybackFinished()));
                publishPlaybackRoute();
            }
            case RecordEffect.StopPlayback ignored -> playbackPort.stop();
            case RecordEffect.SelectPlaybackOutput select -> {
                if (shouldExcludeEarbudsOutput() && isEarbudsOutputId(select.outputId())) {
                    publishPlaybackRoute();
                    return;
                }
                playbackPort.selectOutput(select.outputId());
                publishPlaybackRoute();
            }
            case RecordEffect.ReloadLocal ignored ->
                    dispatch(new RecordEvent.LocalFilesReloaded(recordPort.listLocalSavedFiles()));
            case RecordEffect.Notify notification -> notices.postValue(notification.notice());
        }
    }

    /** 把端口此刻的输出设备事实回灌状态机：不支持主动上下行的机型在录音期间屏蔽耳机输出。 */
    private void publishPlaybackRoute() {
        List<PlaybackOutput> raw = playbackPort.availableOutputs();
        PlaybackOutput current = playbackPort.currentOutput();
        if (shouldExcludeEarbudsOutput()) {
            List<PlaybackOutput> filtered = new ArrayList<>(raw.size());
            for (PlaybackOutput output : raw) {
                if (output.kind() != PlaybackOutput.Kind.EARBUDS) {
                    filtered.add(output);
                }
            }
            if (current == null || current.kind() == PlaybackOutput.Kind.EARBUDS) {
                PlaybackOutput fallback = findFallbackOutput(filtered);
                if (fallback != null) {
                    playbackPort.selectOutput(fallback.id());
                    current = fallback;
                } else {
                    current = null;
                }
            }
            dispatch(new RecordEvent.PlaybackRouteChanged(
                    Optional.ofNullable(current), filtered));
            return;
        }
        dispatch(new RecordEvent.PlaybackRouteChanged(
                Optional.ofNullable(current), raw));
    }

    private synchronized boolean isRecordingActiveLocked() {
        return state.stream().active()
                || state.deviceRecord().phase() == RecordState.DeviceRecordPhase.STARTING
                || state.deviceRecord().phase() == RecordState.DeviceRecordPhase.ACTIVE;
    }

    private boolean shouldExcludeEarbudsOutput() {
        return isRecordingActiveLocked() && !activeDuplexSupported;
    }

    private boolean isEarbudsOutputId(@NonNull String outputId) {
        for (PlaybackOutput output : playbackPort.availableOutputs()) {
            if (output.id().equals(outputId) && output.kind() == PlaybackOutput.Kind.EARBUDS) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static PlaybackOutput findFallbackOutput(@NonNull List<PlaybackOutput> outputs) {
        for (PlaybackOutput output : outputs) {
            if (output.kind() == PlaybackOutput.Kind.PHONE_SPEAKER) {
                return output;
            }
        }
        return outputs.isEmpty() ? null : outputs.get(0);
    }

    /**
     * 端口结果的唯一次分派：成功 / 失败各走一个事件，不把 {@code null} 或布尔哨兵放进状态机。
     */
    private static void onOutcome(@NonNull RecordOutcome outcome, @NonNull Runnable onSuccess,
                                  @NonNull Consumer<RecordFailure> onFailure) {
        switch (outcome) {
            case RecordOutcome.Ok ignored -> onSuccess.run();
            case RecordOutcome.Failed failed -> onFailure.accept(failed.failure());
        }
    }

    private void onDeviceSnapshot(@NonNull DeviceSnapshot snapshot) {
        if (snapshot.connection() == ConnectionState.READY) {
            activeDuplexSupported = catalog != null
                    && snapshot.modelId() != null
                    && catalog.model(snapshot.modelId())
                            .map(p -> p.supports(DeviceCapabilityIds.ACTIVE_DUPLEX))
                            .orElse(false);
            dispatch(new RecordEvent.Connected(
                    recordPort.supportedModes(), snapshot.recordFiles()));
        } else {
            activeDuplexSupported = false;
            dispatch(new RecordEvent.Disconnected());
        }
    }

    @Override
    protected void onCleared() {
        ticker.removeCallbacks(tickTask);
        ticker.removeCallbacks(playbackTickTask);
        synchronized (this) {
            deviceTicking = false;
            playbackTicking = false;
        }
        playbackPort.stop();
        playbackPort.release();
        deviceSubscription.unsubscribe();
        super.onCleared();
    }

    /** 端口 → 事件：随录随传。 */
    private final class PortStreamObserver implements RecordPort.StreamRecordObserver {

        @Override
        public void onStateChanged(@NonNull StreamAudioSessionState reported) {
            dispatch(new RecordEvent.StreamState(reported));
        }

        @Override
        public void onFileSaved(@NonNull SavedRecordItem item) {
            dispatch(new RecordEvent.StreamSaved(item));
        }

        @Override
        public void onError(@NonNull RecordFailure failure) {
            dispatch(new RecordEvent.StreamFailed(failure));
        }
    }

    /** 端口 → 事件：文件同步。文件 id 由发起方携带，不依赖回调里的可空字段。 */
    private final class PortSyncObserver implements RecordPort.SyncProgressObserver {

        private final String fileId;

        private PortSyncObserver(@NonNull String fileId) {
            this.fileId = fileId;
        }

        @Override
        public void onProgress(@NonNull SyncProgressState progress) {
            dispatch(new RecordEvent.SyncProgress(fileId, progress.percentage(),
                    progress.bytesReceived(), progress.totalBytes()));
        }

        @Override
        public void onSuccess(@NonNull SavedRecordItem savedFile) {
            dispatch(new RecordEvent.SyncSucceeded(savedFile));
        }

        @Override
        public void onError(@NonNull RecordFailure failure) {
            dispatch(new RecordEvent.SyncFailed(failure));
        }
    }
}
