package com.ggec.earbuds.ui.device.record;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;
import com.ggec.earbuds.domain.device.model.SyncProgressState;
import com.ggec.earbuds.domain.device.port.PcmPlaybackPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 录音功能 ViewModel：驱动随录随传和耳机本地录音业务流程。
 */
public final class RecordViewModel extends ViewModel {

    public interface Ticker {
        void postDelayed(@NonNull Runnable runnable, long delayMs);
        void removeCallbacks(@NonNull Runnable runnable);
    }

    private final RecordPort recordPort;
    private final ObserveDevice observeDevice;
    private final PcmPlaybackPort pcmPlaybackPort;
    private final Ticker ticker;

    private final MutableLiveData<RecordUiState> uiState = new MutableLiveData<>(RecordUiState.initial());
    private final OneShotLiveData<RecordEvent> events = new OneShotLiveData<>();

    private final Subscription deviceSubscription;

    private RecordMode currentMode = RecordMode.STREAM;
    private boolean isConnected = false;
    private String deviceName = "";

    // 随录随传
    private boolean isStreamRecording = false;
    private boolean isStreamDataReceiving = false;
    private long streamDataStartWallTime = 0L;
    private long streamDurationMs = 0L;
    private float streamAmplitude = 0f;

    // 本地已存文件
    private List<SavedRecordItem> localFiles = new ArrayList<>();
    private String playingFilePath = null;

    // 耳机端本地录音
    private List<RecordFileView> deviceFiles = new ArrayList<>();
    private boolean isDeviceRecording = false;
    private long deviceRecordStartTime = 0L;
    private long deviceRecordDurationMs = 0L;
    private boolean isRefreshingDevice = false;

    // 定时器 Runnable
    private final Runnable deviceRecordTick = new Runnable() {
        @Override
        public void run() {
            if (isDeviceRecording) {
                deviceRecordDurationMs = SystemClock.elapsedRealtime() - deviceRecordStartTime;
                publish();
                ticker.postDelayed(this, 1000L);
            }
        }
    };

    // 同步传输
    private boolean isSyncing = false;
    private String syncingFileId = null;
    private int syncProgressPercent = 0;
    private long syncTransferredBytes = 0;
    private long syncTotalBytes = 0;

    public RecordViewModel(@NonNull RecordPort recordPort,
                           @NonNull ObserveDevice observeDevice,
                           @NonNull PcmPlaybackPort pcmPlaybackPort,
                           @NonNull Ticker ticker) {
        this.recordPort = Objects.requireNonNull(recordPort, "recordPort");
        this.observeDevice = Objects.requireNonNull(observeDevice, "observeDevice");
        this.pcmPlaybackPort = Objects.requireNonNull(pcmPlaybackPort, "pcmPlaybackPort");
        this.ticker = Objects.requireNonNull(ticker, "ticker");

        reloadLocalFiles();

        this.deviceSubscription = observeDevice.execute(this::onDeviceSnapshot);
        onDeviceSnapshot(observeDevice.current());
    }

    @NonNull
    public LiveData<RecordUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<RecordEvent> events() {
        return events;
    }

    private void onDeviceSnapshot(@NonNull DeviceSnapshot snapshot) {
        this.isConnected = snapshot.isConnected();
        this.deviceName = snapshot.bleName().isEmpty() ? "蓝牙耳机" : snapshot.bleName();
        if (snapshot.recordFiles() != null && !snapshot.recordFiles().isEmpty()) {
            this.deviceFiles = new ArrayList<>(snapshot.recordFiles());
        }
        publish();
    }

    private void publish() {
        uiState.postValue(new RecordUiState(
                currentMode,
                isConnected,
                deviceName,
                isStreamRecording,
                isStreamDataReceiving,
                streamDurationMs,
                streamAmplitude,
                localFiles,
                playingFilePath,
                deviceFiles,
                isDeviceRecording,
                deviceRecordDurationMs,
                isRefreshingDevice,
                isSyncing,
                syncingFileId,
                syncProgressPercent,
                syncTransferredBytes,
                syncTotalBytes
        ));
    }

    public void setMode(@NonNull RecordMode mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            publish();
        }
    }

    // ==================== 随录随传 ====================

    public void startStreamRecord() {
        if (isStreamRecording) {
            return;
        }
        if (!isConnected) {
            events.postValue(new RecordEvent.ToastMessage("耳机未连接，无法开始录音"));
            return;
        }

        isStreamRecording = true;
        isStreamDataReceiving = false;
        streamDataStartWallTime = 0L;
        streamDurationMs = 0L;
        streamAmplitude = 0f;
        publish();

        recordPort.startStreamRecord(null, null, new RecordPort.StreamRecordObserver() {
            @Override
            public void onStateChanged(@NonNull StreamAudioSessionState state) {
                isStreamRecording = state.status() == StreamAudioSessionState.Status.RECORDING;
                if (!isStreamDataReceiving && state.durationMs() > 0) {
                    isStreamDataReceiving = true;
                    streamDataStartWallTime = SystemClock.elapsedRealtime();
                }
                if (isStreamDataReceiving) {
                    streamDurationMs = SystemClock.elapsedRealtime() - streamDataStartWallTime;
                } else {
                    streamDurationMs = 0L;
                }
                streamAmplitude = state.currentAmplitude();
                publish();
            }

            @Override
            public void onFileSaved(@NonNull SavedRecordItem item) {
                isStreamRecording = false;
                isStreamDataReceiving = false;
                streamDurationMs = 0L;
                streamAmplitude = 0f;
                reloadLocalFiles();
                events.postValue(new RecordEvent.ToastMessage("录音已保存: " + item.fileName()));
            }

            @Override
            public void onError(@NonNull String message) {
                isStreamRecording = false;
                isStreamDataReceiving = false;
                streamDurationMs = 0L;
                streamAmplitude = 0f;
                publish();
                events.postValue(new RecordEvent.ToastMessage("录音异常: " + message));
            }
        });
    }

    public void stopStreamRecord() {
        if (!isStreamRecording) {
            return;
        }
        recordPort.stopStreamRecord(success -> {
            if (!success) {
                events.postValue(new RecordEvent.ToastMessage("停止录音指令发送失败"));
            }
        });
    }

    // ==================== 本地已存录音管理 ====================

    public void reloadLocalFiles() {
        this.localFiles = recordPort.listLocalSavedFiles();
        publish();
    }

    public void togglePlayLocalFile(@NonNull SavedRecordItem item) {
        if (playingFilePath != null && playingFilePath.equals(item.filePath())) {
            stopPlayback();
            return;
        }

        stopPlayback();

        playingFilePath = item.filePath();
        publish();

        pcmPlaybackPort.play(item.filePath(), () -> {
            playingFilePath = null;
            publish();
        });
    }

    private void stopPlayback() {
        pcmPlaybackPort.stop();
        playingFilePath = null;
        publish();
    }

    public void deleteLocalFile(@NonNull SavedRecordItem item) {
        if (playingFilePath != null && playingFilePath.equals(item.filePath())) {
            stopPlayback();
        }
        boolean deleted = recordPort.deleteLocalSavedFile(item.filePath());
        if (deleted) {
            reloadLocalFiles();
            events.postValue(new RecordEvent.ToastMessage("已删除录音文件"));
        } else {
            events.postValue(new RecordEvent.ToastMessage("删除失败"));
        }
    }

    // ==================== 耳机本地录音控制 ====================

    public void startDeviceRecord() {
        if (!isConnected) {
            events.postValue(new RecordEvent.ToastMessage("耳机未连接"));
            return;
        }
        recordPort.startDeviceRecord(success -> {
            if (success) {
                isDeviceRecording = true;
                deviceRecordStartTime = SystemClock.elapsedRealtime();
                deviceRecordDurationMs = 0L;
                ticker.removeCallbacks(deviceRecordTick);
                ticker.postDelayed(deviceRecordTick, 1000L);
                publish();
                events.postValue(new RecordEvent.ToastMessage("耳机已开始本地录制"));
            } else {
                events.postValue(new RecordEvent.ToastMessage("启动耳机录音失败"));
            }
        });
    }

    public void stopDeviceRecord() {
        if (!isConnected) {
            events.postValue(new RecordEvent.ToastMessage("耳机未连接"));
            return;
        }
        recordPort.stopDeviceRecord(success -> {
            if (success) {
                isDeviceRecording = false;
                ticker.removeCallbacks(deviceRecordTick);
                deviceRecordDurationMs = 0L;
                publish();
                events.postValue(new RecordEvent.ToastMessage("耳机已停止本地录制"));
                // 停止后主动拉取刷新一次耳机文件列表
                refreshDeviceRecords();
            } else {
                events.postValue(new RecordEvent.ToastMessage("停止耳机录音失败"));
            }
        });
    }

    /**
     * 手动刷新耳机内录音列表（根据规范，严禁自动调用）。
     */
    public void refreshDeviceRecords() {
        if (!isConnected) {
            events.postValue(new RecordEvent.ToastMessage("耳机未连接"));
            return;
        }
        isRefreshingDevice = true;
        publish();

        recordPort.refreshDeviceRecords(success -> {
            isRefreshingDevice = false;
            publish();
            if (!success) {
                events.postValue(new RecordEvent.ToastMessage("刷新耳机录音列表失败"));
            }
        });
    }

    public void syncDeviceRecord(@NonNull String fileId) {
        if (isSyncing) {
            events.postValue(new RecordEvent.ToastMessage("已有同步任务正在进行中"));
            return;
        }
        if (!isConnected) {
            events.postValue(new RecordEvent.ToastMessage("耳机未连接"));
            return;
        }

        isSyncing = true;
        syncingFileId = fileId;
        syncProgressPercent = 0;
        syncTransferredBytes = 0;
        syncTotalBytes = 0;
        publish();

        recordPort.syncDeviceRecord(fileId, new RecordPort.SyncProgressObserver() {
            @Override
            public void onProgress(@NonNull SyncProgressState progress) {
                syncProgressPercent = progress.percentage();
                syncTransferredBytes = progress.bytesReceived();
                syncTotalBytes = progress.totalBytes();
                publish();
            }

            @Override
            public void onSuccess(@NonNull SavedRecordItem savedFile) {
                isSyncing = false;
                syncingFileId = null;
                reloadLocalFiles();
                events.postValue(new RecordEvent.ToastMessage("文件同步成功: " + savedFile.fileName()));
            }

            @Override
            public void onError(@NonNull String message) {
                isSyncing = false;
                syncingFileId = null;
                publish();
                events.postValue(new RecordEvent.ToastMessage("文件同步失败: " + message));
            }
        });
    }

    public void cancelSync() {
        if (!isSyncing) {
            return;
        }
        recordPort.cancelSyncDeviceRecord(success -> {
            isSyncing = false;
            syncingFileId = null;
            publish();
            events.postValue(new RecordEvent.ToastMessage("已取消文件同步"));
        });
    }

    public void deleteDeviceRecord(@NonNull String fileId) {
        if (!isConnected) {
            events.postValue(new RecordEvent.ToastMessage("耳机未连接"));
            return;
        }
        recordPort.deleteDeviceRecord(fileId, success -> {
            if (success) {
                events.postValue(new RecordEvent.ToastMessage("已发送删除指令"));
                refreshDeviceRecords();
            } else {
                events.postValue(new RecordEvent.ToastMessage("删除耳机文件失败"));
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ticker.removeCallbacks(deviceRecordTick);
        stopPlayback();
        pcmPlaybackPort.release();
        deviceSubscription.unsubscribe();
    }
}
