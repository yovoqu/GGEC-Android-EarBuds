package com.yovo.lib_bluetooth.internal.common.permission;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单项蓝牙权限的检查结果，包含是否授予及拒绝原因。
 */

public final class YoBluetoothPermissionResult {
    private final YoBluetoothPermission permission;
    private final boolean granted;
    private final String deniedReason;

    public YoBluetoothPermissionResult(@NonNull YoBluetoothPermission permission,
                                       boolean granted,
                                       @Nullable String deniedReason) {
        this.permission = permission;
        this.granted = granted;
        this.deniedReason = granted ? null : (deniedReason != null ? deniedReason : "Unknown reason");
    }

    @NonNull
    public YoBluetoothPermission permission() {
        return permission;
    }

    public boolean isGranted() {
        return granted;
    }

    @Nullable
    public String deniedReason() {
        return deniedReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoBluetoothPermissionResult)) return false;
        YoBluetoothPermissionResult that = (YoBluetoothPermissionResult) o;
        return granted == that.granted
                && permission == that.permission
                && Objects.equals(deniedReason, that.deniedReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(permission, granted, deniedReason);
    }

    @NonNull
    @Override
    public String toString() {
        return "YoBluetoothPermissionResult{" +
                "permission=" + permission +
                ", granted=" + granted +
                ", deniedReason='" + deniedReason + '\'' +
                '}';
    }
}
