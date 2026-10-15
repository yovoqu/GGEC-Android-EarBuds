package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 启动时恢复下载目录内未完成任务（进程被杀后断点续传）。
 */
public interface PendingDownloadResumer {

    void resume(@NonNull File rootDir, @NonNull String apkFilePrefix);
}
