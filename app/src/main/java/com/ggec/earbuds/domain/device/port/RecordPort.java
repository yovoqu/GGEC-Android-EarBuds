package com.ggec.earbuds.domain.device.port;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.RecordOutcome;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;
import com.ggec.earbuds.domain.device.model.SyncProgressState;
import com.ggec.earbuds.domain.device.record.RecordFailure;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 录音业务领域端口抽象（随录随传、设备本地录音、本地已存文件管理）。
 *
 * <p>上层 UI 和 ViewModel 仅与本端口交互，严禁依赖底层 SDK 内部实现。</p>
 *
 * <p><b>失败通道：</b>所有结果都是 {@link RecordOutcome} 或 {@link RecordFailure} 分类，
 * 端口不传递自由文本；没有当前设备时由装配层的网关给出 {@link RecordFailure#NOT_CONNECTED}。
 * 分类定义单点在 {@code domain/device/record/RecordFailure}，录音状态机与界面共用同一组。</p>
 */
public interface RecordPort {

    // ---------- 随录随传 ----------

    interface StreamRecordObserver {
        void onStateChanged(@NonNull StreamAudioSessionState state);
        void onFileSaved(@NonNull SavedRecordItem item);
        void onError(@NonNull RecordFailure failure);
    }

    /** 启动随录随传（按 provider 默认落盘位置同时分发实时状态与波形幅度） */
    void startStreamRecord(@NonNull StreamRecordObserver observer);

    /** 停止随录随传 */
    void stopStreamRecord(@NonNull Consumer<RecordOutcome> onComplete);

    boolean isStreamRecording();

    // ---------- 设备本地录音 ----------

    /** 控制耳机开始本地录音 */
    void startDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete);

    /** 控制耳机结束本地录音 */
    void stopDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete);

    /** 手动请求刷新设备内的录音文件列表 */
    void refreshDeviceRecords(@NonNull Consumer<RecordOutcome> onComplete);

    interface SyncProgressObserver {
        void onProgress(@NonNull SyncProgressState progress);
        void onSuccess(@NonNull SavedRecordItem savedFile);
        void onError(@NonNull RecordFailure failure);
    }

    /** 同步设备内录音文件到手机本地 */
    void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer);

    /** 取消当前正在进行的文件同步 */
    void cancelSyncDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete);

    /** 删除设备内的录音文件 */
    void deleteDeviceRecord(@NonNull String fileId, @NonNull Consumer<RecordOutcome> onComplete);

    // ---------- 本地已存文件管理 ----------

    /** 查询手机本地保存的录音文件列表（含随录与同步产生的文件） */
    List<SavedRecordItem> listLocalSavedFiles();

    /** 删除手机本地录音文件 */
    RecordOutcome deleteLocalSavedFile(@NonNull String filePath);

    /** 当前设备支持的录音模式集合；空集表示这台设备不支持录音 */
    @NonNull
    Set<RecordMode> supportedModes();
}
