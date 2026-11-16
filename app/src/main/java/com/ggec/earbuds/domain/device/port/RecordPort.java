package com.ggec.earbuds.domain.device.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;
import com.ggec.earbuds.domain.device.model.SyncProgressState;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * 录音业务领域端口抽象（随录随传、设备本地录音、本地已存文件管理）。
 *
 * <p>上层 UI 和 ViewModel 仅与本端口交互，严禁依赖底层 SDK 内部实现。</p>
 */
public interface RecordPort {

    // ---------- 随录随传 ----------

    interface StreamRecordObserver {
        void onStateChanged(@NonNull StreamAudioSessionState state);
        void onFileSaved(@NonNull SavedRecordItem item);
        void onError(@NonNull String message);
    }

    /** 启动随录随传（默认落盘为 MP3 文件，同时向观察者派发实时状态与波形幅度） */
    void startStreamRecord(@Nullable String customFileName, @Nullable File customSaveDir,
                           @NonNull StreamRecordObserver observer);

    /** 停止随录随传 */
    void stopStreamRecord(@NonNull Consumer<Boolean> onComplete);

    boolean isStreamRecording();

    // ---------- 设备本地录音 ----------

    /** 控制耳机开始本地录音 */
    void startDeviceRecord(@NonNull Consumer<Boolean> onComplete);

    /** 控制耳机结束本地录音 */
    void stopDeviceRecord(@NonNull Consumer<Boolean> onComplete);

    /** 手动请求刷新设备内的录音文件列表 */
    void refreshDeviceRecords(@NonNull Consumer<Boolean> onComplete);

    interface SyncProgressObserver {
        void onProgress(@NonNull SyncProgressState progress);
        void onSuccess(@NonNull SavedRecordItem savedFile);
        void onError(@NonNull String message);
    }

    /** 同步设备内录音文件到手机本地 */
    void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer);

    /** 取消当前正在进行的文件同步 */
    void cancelSyncDeviceRecord(@NonNull Consumer<Boolean> onComplete);

    /** 删除设备内的录音文件 */
    void deleteDeviceRecord(@NonNull String fileId, @NonNull Consumer<Boolean> onComplete);

    // ---------- 本地已存文件管理 ----------

    /** 查询手机本地保存的录音文件列表（含随录与同步产生的文件） */
    List<SavedRecordItem> listLocalSavedFiles();

    /** 删除手机本地录音文件 */
    boolean deleteLocalSavedFile(@NonNull String filePath);
}
