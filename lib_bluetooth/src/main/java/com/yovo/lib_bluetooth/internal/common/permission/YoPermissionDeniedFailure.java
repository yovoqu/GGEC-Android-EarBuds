package com.yovo.lib_bluetooth.internal.common.permission;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.error.YoCommonErrorCode;
import com.yovo.lib_bluetooth.internal.common.result.YoFailure;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙权限被拒绝时的结构化失败信息。
 */

public final class YoPermissionDeniedFailure extends YoFailure {

    private final YoBluetoothPermission deniedPermission;

    public YoPermissionDeniedFailure(@NonNull YoBluetoothPermission deniedPermission,
                                     @NonNull String message,
                                     @Nullable Throwable cause,
                                     @NonNull String sourceModule,
                                     @NonNull String traceId) {
        super(YoCommonErrorCode.PERMISSION_DENIED,
                message != null ? message : "Permission denied: " + deniedPermission.name(),
                cause,
                false,   // 重试不可恢复，需用户授予权限
                false,   // 不可重试
                sourceModule,
                traceId);
        this.deniedPermission = deniedPermission;
    }

    @NonNull
    public YoBluetoothPermission getDeniedPermission() {
        return deniedPermission;
    }

    @NonNull
    @Override
    public String toString() {
        return "YoPermissionDeniedFailure{"
                + "deniedPermission=" + deniedPermission
                + ", message=" + getMessage()
                + ", errorCode=" + getErrorCode().code()
                + '}';
    }
}
