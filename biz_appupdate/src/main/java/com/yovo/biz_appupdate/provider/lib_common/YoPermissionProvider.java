package com.yovo.biz_appupdate.provider.lib_common;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.DownloadDirPermissionPort;
import com.yovo.biz_appupdate.internal.contract.InstallPermissionPort;
import com.yovo.biz_appupdate.provider.lib_common.DownloadDirPermissionPolicy;
import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;
import com.yovo.lib_common.system.permission.YoPermission;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;

import java.util.Collections;
import java.util.List;

/**
 * lib_common 权限能力 Provider。
 *
 * <p>只暴露 biz_appupdate 需要的权限能力，作为 API 过滤层与隔离层。</p>
 */
public final class YoPermissionProvider implements InstallPermissionPort,
        DownloadDirPermissionPort {

    private final Context appContext;

    public YoPermissionProvider(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public boolean canInstallUnknownSources() {
        return YoPermission.isGranted(appContext,
                PermissionPolicy.INSTALL_UNKNOWN_SOURCES.requiredFor(Build.VERSION.SDK_INT));
    }

    @Override
    public boolean requestIfNeeded(@NonNull Activity activity) {
        if (canInstallUnknownSources()) {
            return true;
        }
        List<Permission> required = PermissionPolicy.INSTALL_UNKNOWN_SOURCES
                .requiredFor(Build.VERSION.SDK_INT);
        if (required.isEmpty()) {
            return true;
        }
        YoPermission.requestSpecialAccess(activity, required, (granted, denied) -> { });
        return false;
    }

    @Override
    public boolean isGranted(@NonNull DownloadDirSpec.Type dirType) {
        Permission required = DownloadDirPermissionPolicy.requiredFor(
                Build.VERSION.SDK_INT, dirType);
        return required == null || YoPermission.isGranted(appContext, required);
    }

    @Override
    public boolean requestIfNeeded(@NonNull Activity activity,
                                   @NonNull DownloadDirSpec.Type dirType) {
        if (isGranted(dirType)) {
            return true;
        }
        Permission required = DownloadDirPermissionPolicy.requiredFor(
                Build.VERSION.SDK_INT, dirType);
        if (required == null) {
            return true;
        }
        if (required.isSpecialAccess()) {
            YoPermission.requestSpecialAccess(
                    activity, Collections.singletonList(required), (granted, denied) -> { });
            return false;
        }
        return YoPermission.requestIfNeeded(activity, required);
    }
}
