package com.ggec.earbuds.provider.biz_ggec_wy.record;

import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.RecordOutcome;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.record.RecordFailure;
import com.yovo.biz_ggec_wy.YoWySdk;
import com.yovo.biz_ggec_wy.api.StreamRecordConfig;
import com.yovo.biz_ggec_wy.api.StreamRecordListener;
import com.yovo.biz_ggec_wy.api.WyControl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 把 {@code biz_ggec_wy} 的录音能力适配为应用领域的 {@link RecordPort}。
 *
 * <p>WY 的 SDK 只提供随录随传（{@link RecordMode#STREAM}）：设备本地录音、设备文件列表、
 * 文件同步与设备内删除都<b>没有</b>对应 API。这些能力按
 * {@link RecordFailure#UNSUPPORTED} 明确失败，不返回恒真/恒假来冒充实现。</p>
 */
public final class WyRecordAdapter implements RecordPort {

    /** 随录随传按 provider 默认位置同时分发实时 PCM 并落盘；端口契约不再带自定义文件名/目录。 */
    private static final StreamRecordConfig STREAM_CONFIG = StreamRecordConfig.both(null, null);

    private final YoWySdk sdk;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private StreamRecordObserver activeStreamObserver;
    private long streamStartTime;
    private Runnable durationTicker;

    public WyRecordAdapter(@NonNull YoWySdk sdk) {
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
                // 随录随传的失败只给自由文本，文本不出 provider。
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
        rejectUnsupported(onComplete);
    }

    @Override
    public void stopDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        rejectUnsupported(onComplete);
    }

    @Override
    public void refreshDeviceRecords(@NonNull Consumer<RecordOutcome> onComplete) {
        rejectUnsupported(onComplete);
    }

    @Override
    public void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer) {
        Objects.requireNonNull(observer, "observer");
        mainHandler.post(() -> observer.onError(RecordFailure.UNSUPPORTED));
    }

    @Override
    public void cancelSyncDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        rejectUnsupported(onComplete);
    }

    @Override
    public void deleteDeviceRecord(@NonNull String fileId,
                                   @NonNull Consumer<RecordOutcome> onComplete) {
        rejectUnsupported(onComplete);
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

    @NonNull
    @Override
    public Set<RecordMode> supportedModes() {
        return Set.of(RecordMode.STREAM);
    }

    // ---- 结果与失败分类 ----

    /** WY 没有该能力：统一以「模式不支持」失败，调用方据此渲染禁用态。 */
    private void rejectUnsupported(@NonNull Consumer<RecordOutcome> onComplete) {
        mainHandler.post(() -> onComplete.accept(RecordOutcome.failed(RecordFailure.UNSUPPORTED)));
    }

    @NonNull
    private static RecordOutcome toRecordOutcome(@NonNull WyControl.Outcome<Void> result) {
        return switch (result) {
            case WyControl.Outcome.Ok<Void> ignored -> RecordOutcome.ok();
            case WyControl.Outcome.Err<Void> failed ->
                    RecordOutcome.failed(toRecordFailure(failed.failure()));
        };
    }

    /**
     * SDK 失败 → 录音分类（映射单点在 provider）。
     *
     * <p>断开类原因归「没有活动连接」；蓝牙/权限不可用时链路事实上不存在，同一处理；
     * 扫描失败不会出现在录音调用上，显式合并到设备错误，不用 {@code default} 吞掉。</p>
     */
    @NonNull
    private static RecordFailure toRecordFailure(@NonNull WyControl.Failure failure) {
        return switch (failure) {
            case DISCONNECTED, CLASSIC_NOT_CONNECTED -> RecordFailure.NOT_CONNECTED;
            case TIMEOUT -> RecordFailure.TIMEOUT;
            case REJECTED -> RecordFailure.REJECTED;
            case UNSUPPORTED_CAPABILITY -> RecordFailure.UNSUPPORTED;
            case BLUETOOTH_DISABLED, PERMISSION_DENIED -> RecordFailure.NOT_CONNECTED;
            case DEVICE_ERROR -> RecordFailure.DEVICE_ERROR;
            case SCAN_FAILED, INTERNAL_ERROR -> RecordFailure.DEVICE_ERROR;
        };
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
