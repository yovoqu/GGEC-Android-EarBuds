package com.yovo.biz_ggec_hs.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;

/**
 * 耳机录音业务 API 契约（包含随录随传、设备本地录音、文件传输与本地录音文件管理）。
 */
public interface HsRecordApi {

    // ---------- 随录随传（Stream Record） ----------

    /**
     * 开始随录随传。
     *
     * @param config 录音配置（输出模式、自定义路径或默认落盘）
     * @param listener 状态、PCM 数据流与文件完成监听
     */
    void startStreamRecord(@NonNull StreamRecordConfig config, @NonNull StreamRecordListener listener);

    /**
     * 停止随录随传。
     */
    void stopStreamRecord(@NonNull HsControl.Callback callback);

    /**
     * 当前是否正在随录随传。
     */
    boolean isStreamRecording();

    // ---------- 本地录音（Device Local Record） ----------

    /**
     * 控制耳机端开始本地录音。
     */
    void startDeviceRecord(@NonNull HsControl.Callback callback);

    /**
     * 控制耳机端停止本地录音。
     */
    void stopDeviceRecord(@NonNull HsControl.Callback callback);

    /**
     * 手动刷新获取耳机端内部录音文件列表。
     */
    void refreshDeviceRecords(@NonNull HsControl.Callback callback);

    /**
     * 同步/导出指定设备录音文件到手机。
     *
     * @param fileId 设备内文件 ID
     * @param targetFile 手机端目标保存文件（为 null 则保存到默认 transfer 目录）
     * @param listener 进度与结果监听器
     */
    void syncDeviceRecord(@NonNull String fileId, @Nullable File targetFile,
                          @NonNull SyncProgressListener listener);

    /**
     * 取消正在进行的文件同步。
     */
    void cancelSyncDeviceRecord(@NonNull HsControl.Callback callback);

    /**
     * 删除耳机端指定的录音文件。
     */
    void deleteDeviceRecord(@NonNull String fileId, @NonNull HsControl.Callback callback);

    // ---------- 手机端已存录音文件统一管理 ----------

    /**
     * 获取录音文件本地保存的根目录。
     */
    @NonNull
    File getRecordStorageDirectory();

    /**
     * 获取手机端已保存的所有录音文件列表（按时间倒序排列）。
     */
    @NonNull
    List<File> listLocalSavedFiles();

    /**
     * 删除手机端指定的已存录音文件。
     *
     * @param filePath 文件绝对路径
     * @return 是否成功删除
     */
    boolean deleteLocalSavedFile(@NonNull String filePath);
}
