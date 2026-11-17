package com.ggec.earbuds.provider.biz_ggec_wy;

import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;
import com.ggec.earbuds.domain.device.model.SyncProgressState;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.yovo.biz_ggec_wy.YoWySdk;
import com.yovo.biz_ggec_wy.api.StreamRecordConfig;
import com.yovo.biz_ggec_wy.api.StreamRecordListener;
import com.yovo.biz_ggec_wy.api.WyControl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 把 {@code biz_ggec_wy} 的录音能力适配为应用领域的 {@link RecordPort}。
 */
public final class WyRecordAdapter implements RecordPort {

    private final YoWySdk sdk;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private StreamRecordObserver activeStreamObserver;
    private long streamStartTime;
    private Runnable durationTicker;

    public WyRecordAdapter(@NonNull YoWySdk sdk) {
        this.sdk = Objects.requireNonNull(sdk, "sdk");
    }

    @Override
    public void startStreamRecord(@Nullable String customFileName, @Nullable File customSaveDir,
                                  @NonNull StreamRecordObserver observer) {
        this.activeStreamObserver = observer;
        this.streamStartTime = System.currentTimeMillis();

        StreamRecordConfig config = StreamRecordConfig.both(customFileName, customSaveDir);
        sdk.record().startStreamRecord(config, new StreamRecordListener() {
            @Override
            public void onStarted() {
                mainHandler.post(() -> {
                    startDurationTicker();
                    observer.onStateChanged(new StreamAudioSessionState(
                            StreamAudioSessionState.Status.RECORDING, 0, 0f, customFileName));
                });
            }

            @Override
            public void onPcmFrame(@NonNull byte[] pcmData, float amplitude) {
                mainHandler.post(() -> {
                    long duration = System.currentTimeMillis() - streamStartTime;
                    observer.onStateChanged(new StreamAudioSessionState(
                            StreamAudioSessionState.Status.RECORDING, duration, amplitude, customFileName));
                });
            }

            @Override
            public void onFileSaved(@NonNull File file, long durationMs, long fileSizeBytes) {
                mainHandler.post(() -> {
                    stopDurationTicker();
                    observer.onFileSaved(SavedRecordItem.of(file, durationMs));
                });
            }

            @Override
            public void onError(@NonNull String message) {
                mainHandler.post(() -> {
                    stopDurationTicker();
                    observer.onError(message);
                    observer.onStateChanged(StreamAudioSessionState.idle());
                });
            }

            @Override
            public void onStopped() {
                mainHandler.post(() -> {
                    stopDurationTicker();
                    observer.onStateChanged(StreamAudioSessionState.idle());
                });
            }
        });
    }

    @Override
    public void stopStreamRecord(@NonNull Consumer<Boolean> onComplete) {
        stopDurationTicker();
        sdk.record().stopStreamRecord(result -> {
            boolean success = result instanceof WyControl.Outcome.Ok;
            mainHandler.post(() -> onComplete.accept(success));
        });
    }

    @Override
    public boolean isStreamRecording() {
        return sdk.record().isStreamRecording();
    }

    @Override
    public void startDeviceRecord(@NonNull Consumer<Boolean> onComplete) {
        mainHandler.post(() -> onComplete.accept(false));
    }

    @Override
    public void stopDeviceRecord(@NonNull Consumer<Boolean> onComplete) {
        mainHandler.post(() -> onComplete.accept(false));
    }

    @Override
    public void refreshDeviceRecords(@NonNull Consumer<Boolean> onComplete) {
        mainHandler.post(() -> onComplete.accept(true));
    }

    @Override
    public void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer) {
        mainHandler.post(() -> observer.onError("WY01 does not support device offline sync"));
    }

    @Override
    public void cancelSyncDeviceRecord(@NonNull Consumer<Boolean> onComplete) {
        mainHandler.post(() -> onComplete.accept(true));
    }

    @Override
    public void deleteDeviceRecord(@NonNull String fileId, @NonNull Consumer<Boolean> onComplete) {
        mainHandler.post(() -> onComplete.accept(false));
    }

    @NonNull
    @Override
    public List<SavedRecordItem> listLocalSavedFiles() {
        return Collections.emptyList();
    }

    @Override
    public boolean deleteLocalSavedFile(@NonNull String filePath) {
        File file = new File(filePath);
        return file.exists() && file.delete();
    }

    @NonNull
    @Override
    public Set<RecordMode> supportedModes() {
        return Set.of(RecordMode.STREAM);
    }

    private void startDurationTicker() {
        stopDurationTicker();
        durationTicker = new Runnable() {
            @Override
            public void run() {
                if (activeStreamObserver != null && sdk.record().isStreamRecording()) {
                    long duration = System.currentTimeMillis() - streamStartTime;
                    activeStreamObserver.onStateChanged(new StreamAudioSessionState(
                            StreamAudioSessionState.Status.RECORDING, duration, 0f, null));
                    mainHandler.postDelayed(this, 500);
                }
            }
        };
        mainHandler.postDelayed(durationTicker, 500);
    }

    private void stopDurationTicker() {
        if (durationTicker != null) {
            mainHandler.removeCallbacks(durationTicker);
            durationTicker = null;
        }
    }
}
