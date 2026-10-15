package com.yovo.biz_appupdate.internal.contract;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;

/**
 * 下载目录权限请求端口。
 */
public interface DownloadDirPermissionPort {

    boolean isGranted(@NonNull DownloadDirSpec.Type dirType);

    boolean requestIfNeeded(@NonNull Activity activity,
                            @NonNull DownloadDirSpec.Type dirType);
}
