package com.yovo.lib_common.system.keepalive.internal;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.YoPermission;
import com.yovo.lib_common.system.keepalive.model.KeepAliveConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveResult;
import com.yovo.lib_common.system.keepalive.model.ForegroundServiceTypePolicy;

public final class KeepAlivePreconditionChecker {

    private KeepAlivePreconditionChecker() {}

    /**
     * 系统硬性前置条件：前台服务类型对应的运行时权限缺失时，启动必然失败。
     */
    @NonNull
    public static KeepAliveResult checkHardRequirements(@NonNull Context context,
                                                        @NonNull KeepAliveConfig config) {
        if (config.fgsType() == ForegroundServiceTypePolicy.CONNECTED_DEVICE) {
            if (Permission.BLUETOOTH_CONNECT.isRequired(android.os.Build.VERSION.SDK_INT)
                    && !YoPermission.isGranted(context, Permission.BLUETOOTH_CONNECT)) {
                return KeepAliveResult.of(
                        KeepAliveResult.Code.PRECONDITION_FAILED,
                        "BLUETOOTH_CONNECT not granted");
            }
        }
        return KeepAliveResult.of(KeepAliveResult.Code.SUCCESS);
    }

    /**
     * 应用软性前置条件：通知权限缺失时前台服务仍可启动，仅由严格模式拦截。
     */
    @NonNull
    public static KeepAliveResult checkSoftRequirements(@NonNull Context context,
                                                        @NonNull KeepAliveConfig config) {
        if (Permission.POST_NOTIFICATIONS.isRequired(android.os.Build.VERSION.SDK_INT)
                && !YoPermission.isGranted(context, Permission.POST_NOTIFICATIONS)) {
            return KeepAliveResult.of(
                    KeepAliveResult.Code.PERMISSION_DENIED,
                    "POST_NOTIFICATIONS not granted");
        }
        return KeepAliveResult.of(KeepAliveResult.Code.SUCCESS);
    }
}
