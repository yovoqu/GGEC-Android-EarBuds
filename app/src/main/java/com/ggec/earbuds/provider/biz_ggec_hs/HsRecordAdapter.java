package com.ggec.earbuds.provider.biz_ggec_hs;

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
import com.yovo.biz_ggec_hs.YoHsSdk;
import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.StreamRecordConfig;
import com.yovo.biz_ggec_hs.api.StreamRecordListener;
import com.yovo.biz_ggec_hs.api.SyncProgressListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 把 {@code biz_ggec_hs} 的录音能力适配为应用领域的 {@link RecordPort}。
 */
public final class HsRecordAdapter implements RecordPort {

    private final YoHsSdk sdk;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private StreamRecordObserver activeStreamObserver;
    private long streamStartTime;
    private Runnable durationTicker;

    public HsRecordAdapter(@NonNull YoHsSdk sdk) {
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
            boolean success = result instanceof HsControl.Outcome.Ok;
            mainHandler.post(() -> onComplete.accept(success));
        });
    }

    @Override
    public boolean isStreamRecording() {
        return sdk.record().isStreamRecording();
    }

    @Override
    public void startDeviceRecord(@NonNull Consumer<Boolean> onComplete) {
        sdk.record().startDeviceRecord(result -> {
            boolean success = result instanceof HsControl.Outcome.Ok;
            mainHandler.post(() -> onComplete.accept(success));
        });
    }

    @Override
    public void stopDeviceRecord(@NonNull Consumer<Boolean> onComplete) {
        sdk.record().stopDeviceRecord(result -> {
            boolean success = result instanceof HsControl.Outcome.Ok;
            mainHandler.post(() -> onComplete.accept(success));
        });
    }

    @Override
    public void refreshDeviceRecords(@NonNull Consumer<Boolean> onComplete) {
        sdk.record().refreshDeviceRecords(result -> {
            boolean success = result instanceof HsControl.Outcome.Ok;
            mainHandler.post(() -> onComplete.accept(success));
        });
    }

    @Override
    public void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer) {
        sdk.record().syncDeviceRecord(fileId, null, new SyncProgressListener() {
            @Override
            public void onProgress(int percentage, long bytesReceived, long totalBytes) {
                mainHandler.post(() -> observer.onProgress(new SyncProgressState(
                        fileId, true, percentage, bytesReceived, totalBytes, null)));
            }

            @Override
            public void onSuccess(@NonNull File savedFile) {
                mainHandler.post(() -> {
                    long duration = resolveAudioDuration(savedFile);
                    observer.onProgress(SyncProgressState.idle());
                    observer.onSuccess(SavedRecordItem.of(savedFile, duration));
                });
            }

            @Override
            public void onError(@NonNull String message) {
                mainHandler.post(() -> {
                    observer.onProgress(new SyncProgressState(fileId, false, 0, 0, 0, message));
                    observer.onError(message);
                });
            }

            @Override
            public void onCanceled() {
                mainHandler.post(() -> observer.onProgress(SyncProgressState.idle()));
            }
        });
    }

    @Override
    public void cancelSyncDeviceRecord(@NonNull Consumer<Boolean> onComplete) {
        sdk.record().cancelSyncDeviceRecord(result -> {
            boolean success = result instanceof HsControl.Outcome.Ok;
            mainHandler.post(() -> onComplete.accept(success));
        });
    }

    @Override
    public void deleteDeviceRecord(@NonNull String fileId, @NonNull Consumer<Boolean> onComplete) {
        sdk.record().deleteDeviceRecord(fileId, result -> {
            boolean success = result instanceof HsControl.Outcome.Ok;
            mainHandler.post(() -> onComplete.accept(success));
        });
    }

    @NonNull
    @Override
    public List<SavedRecordItem> listLocalSavedFiles() {
        List<File> files = sdk.record().listLocalSavedFiles();
        List<SavedRecordItem> items = new ArrayList<>(files.size());
        for (File file : files) {
            long duration = resolveAudioDuration(file);
            items.add(SavedRecordItem.of(file, duration));
        }
        return items;
    }

    @Override
    public boolean deleteLocalSavedFile(@NonNull String filePath) {
        return sdk.record().deleteLocalSavedFile(filePath);
    }

    @NonNull
    @Override
    public Set<RecordMode> supportedModes() {
        return Set.of(RecordMode.STREAM, RecordMode.LOCAL);
    }

    private long resolveAudioDuration(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pcm")) {
            return file.length() / 32L;
        }
        if (name.endsWith(".wav")) {
            return Math.max(0, file.length() - 44) / 32L;
        }
        try (MediaMetadataRetriever mmr = new MediaMetadataRetriever()) {
            mmr.setDataSource(file.getAbsolutePath());
            String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                return Long.parseLong(durationStr);
            }
        } catch (Throwable ignored) {
        }
        return 0L;
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
