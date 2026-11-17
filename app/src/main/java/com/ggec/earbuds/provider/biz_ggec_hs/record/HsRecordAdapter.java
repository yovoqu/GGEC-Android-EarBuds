package com.ggec.earbuds.provider.biz_ggec_hs.record;

import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.RecordOutcome;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;
import com.ggec.earbuds.domain.device.model.SyncProgressState;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.record.RecordFailure;
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
 *
 * <p>失败一律映射为 {@link RecordFailure} 分类：SDK 的自由文本不进邻层，
 * {@code SyncProgressState} 只承载结构化进度。</p>
 */
public final class HsRecordAdapter implements RecordPort {

    /** 随录随传按 provider 默认位置同时分发实时 PCM 并落盘；端口契约不再带自定义文件名/目录。 */
    private static final StreamRecordConfig STREAM_CONFIG = StreamRecordConfig.both(null, null);

    private final YoHsSdk sdk;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private StreamRecordObserver activeStreamObserver;
    private long streamStartTime;
    private Runnable durationTicker;

    public HsRecordAdapter(@NonNull YoHsSdk sdk) {
        this.sdk = Objects.requireNonNull(sdk, "sdk");
    }

    @Override
    public void startStreamRecord(@NonNull StreamRecordObserver observer) {
        Objects.requireNonNull(observer, "observer");
        this.activeStreamObserver = observer;
        this.streamStartTime = System.currentTimeMillis();

        sdk.record().startStreamRecord(STREAM_CONFIG, new StreamRecordListener() {
            @Override
            public void onStarted() {
                mainHandler.post(() -> {
                    startDurationTicker();
                    observer.onStateChanged(new StreamAudioSessionState(
                            StreamAudioSessionState.Status.RECORDING, 0, 0f));
                });
            }

            @Override
            public void onPcmFrame(@NonNull byte[] pcmData, float amplitude) {
                mainHandler.post(() -> {
                    long duration = System.currentTimeMillis() - streamStartTime;
                    observer.onStateChanged(new StreamAudioSessionState(
                            StreamAudioSessionState.Status.RECORDING, duration, amplitude));
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
                // 随录随传的失败只给自由文本（录音已在进行中 / 协议侧中断），文本不出 provider。
                mainHandler.post(() -> {
                    stopDurationTicker();
                    observer.onError(RecordFailure.DEVICE_ERROR);
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
    public void stopStreamRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        stopDurationTicker();
        sdk.record().stopStreamRecord(result -> {
            RecordOutcome outcome = toRecordOutcome(result);
            mainHandler.post(() -> onComplete.accept(outcome));
        });
    }

    @Override
    public boolean isStreamRecording() {
        return sdk.record().isStreamRecording();
    }

    @Override
    public void startDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        sdk.record().startDeviceRecord(result -> {
            RecordOutcome outcome = toRecordOutcome(result);
            mainHandler.post(() -> onComplete.accept(outcome));
        });
    }

    @Override
    public void stopDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        sdk.record().stopDeviceRecord(result -> {
            RecordOutcome outcome = toRecordOutcome(result);
            mainHandler.post(() -> onComplete.accept(outcome));
        });
    }

    @Override
    public void refreshDeviceRecords(@NonNull Consumer<RecordOutcome> onComplete) {
        sdk.record().refreshDeviceRecords(result -> {
            RecordOutcome outcome = toRecordOutcome(result);
            mainHandler.post(() -> onComplete.accept(outcome));
        });
    }

    @Override
    public void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer) {
        Objects.requireNonNull(observer, "observer");
        sdk.record().syncDeviceRecord(fileId, null, new SyncProgressListener() {
            @Override
            public void onProgress(int percentage, long bytesReceived, long totalBytes) {
                mainHandler.post(() -> observer.onProgress(new SyncProgressState(
                        fileId, true, percentage, bytesReceived, totalBytes)));
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
                // SDK 的同步失败只给自由文本，没有可分类的原因；文本不出 provider。
                mainHandler.post(() -> {
                    observer.onProgress(new SyncProgressState(fileId, false, 0, 0, 0));
                    observer.onError(RecordFailure.DEVICE_ERROR);
                });
            }

            @Override
            public void onCanceled() {
                mainHandler.post(() -> observer.onProgress(SyncProgressState.idle()));
            }
        });
    }

    @Override
    public void cancelSyncDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        sdk.record().cancelSyncDeviceRecord(result -> {
            RecordOutcome outcome = toRecordOutcome(result);
            mainHandler.post(() -> onComplete.accept(outcome));
        });
    }

    @Override
    public void deleteDeviceRecord(@NonNull String fileId,
                                   @NonNull Consumer<RecordOutcome> onComplete) {
        sdk.record().deleteDeviceRecord(fileId, result -> {
            RecordOutcome outcome = toRecordOutcome(result);
            mainHandler.post(() -> onComplete.accept(outcome));
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

    @NonNull
    @Override
    public RecordOutcome deleteLocalSavedFile(@NonNull String filePath) {
        return sdk.record().deleteLocalSavedFile(filePath)
                ? RecordOutcome.ok()
                : RecordOutcome.failed(RecordFailure.IO);
    }

    @NonNull
    @Override
    public Set<RecordMode> supportedModes() {
        return Set.of(RecordMode.STREAM, RecordMode.LOCAL);
    }

    // ---- 结果与失败分类 ----

    @NonNull
    private static RecordOutcome toRecordOutcome(@NonNull HsControl.Outcome<Void> result) {
        return switch (result) {
            case HsControl.Outcome.Ok<Void> ignored -> RecordOutcome.ok();
            case HsControl.Outcome.Err<Void> failed ->
                    RecordOutcome.failed(toRecordFailure(failed.failure()));
        };
    }

    /**
     * SDK 失败 → 录音分类（映射单点在 provider）。
     *
     * <p>蓝牙/权限/定位不可用时链路事实上不存在，与「没有活动连接」同一处理；扫描启动期的细分
     * 原因不会出现在录音调用上，显式合并到设备错误，逐分支写出，不用 {@code default} 吞掉。</p>
     */
    @NonNull
    private static RecordFailure toRecordFailure(@NonNull HsControl.Failure failure) {
        return switch (failure) {
            case NOT_READY -> RecordFailure.REJECTED;
            case RELEASED -> RecordFailure.NOT_CONNECTED;
            case TIMEOUT -> RecordFailure.TIMEOUT;
            case REJECTED -> RecordFailure.REJECTED;
            case UNSUPPORTED_CAPABILITY -> RecordFailure.UNSUPPORTED;
            case BLUETOOTH_DISABLED, PERMISSION_DENIED, LOCATION_DISABLED ->
                    RecordFailure.NOT_CONNECTED;
            case BLE_UNSUPPORTED -> RecordFailure.UNSUPPORTED;
            case SCAN_THROTTLED -> RecordFailure.BUSY;
            case DEVICE_ERROR -> RecordFailure.DEVICE_ERROR;
            case SCAN_START_FAILED, SCAN_FEATURE_UNSUPPORTED, SCAN_OUT_OF_RESOURCES,
                 SCAN_INTERNAL_ERROR, INTERNAL_ERROR -> RecordFailure.DEVICE_ERROR;
        };
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
            // 元数据解析失败只影响时长展示，文件本身仍然可播放：按 0 时长返回。
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
                            StreamAudioSessionState.Status.RECORDING, duration, 0f));
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
