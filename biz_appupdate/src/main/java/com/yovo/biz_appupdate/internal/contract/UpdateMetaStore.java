package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.model.DownloadedApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.internal.model.UpdateMeta;

import java.io.File;

/**
 * 远程更新元信息持久化端口。
 *
 * <p>记录最近一次服务端返回的版本信息与首次发现时间，以及 SDK 内部下载完成的 APK
 * 路径，供启动时与本地文件修改时间对比。</p>
 */
public interface UpdateMetaStore {

    /** 记录最近一次远程更新信息。 */
    void record(@NonNull RemoteUpdate update);

    /** 记录 SDK 内部下载完成的最新 APK。 */
    void recordDownloadedApk(@NonNull RemoteUpdate update, @NonNull File apkFile);

    /** 读取最近一次元信息，无记录时返回 null。 */
    @Nullable
    UpdateMeta snapshot();

    void clear();
}
