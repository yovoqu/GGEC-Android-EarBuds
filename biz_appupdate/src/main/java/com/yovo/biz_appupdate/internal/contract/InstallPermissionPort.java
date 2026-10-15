package com.yovo.biz_appupdate.internal.contract;

import android.app.Activity;

import androidx.annotation.NonNull;

/**
 * “安装未知来源”能力端口（Android 8+ 特殊权限）。
 */
public interface InstallPermissionPort {

    boolean canInstallUnknownSources();

    boolean requestIfNeeded(@NonNull Activity activity);
}
