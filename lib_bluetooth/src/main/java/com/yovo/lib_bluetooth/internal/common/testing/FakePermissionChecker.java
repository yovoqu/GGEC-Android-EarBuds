package com.yovo.lib_bluetooth.internal.common.testing;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 测试用 Fake 权限检查器，可模拟授予或拒绝指定权限。
 */
public final class FakePermissionChecker {
    private final Set<YoBluetoothPermission> grantedPermissions;
    private final Set<YoBluetoothPermission> deniedPermissions;

    public FakePermissionChecker() {
        this.grantedPermissions = EnumSet.noneOf(YoBluetoothPermission.class);
        this.deniedPermissions = EnumSet.noneOf(YoBluetoothPermission.class);
    }

    /**
     * 授予指定权限。此后对 {@link #has} 的调用将返回 true
     * （除非该权限同时处于拒绝集合中）。
     */
    public void grant(@NonNull YoBluetoothPermission permission) {
        grantedPermissions.add(permission);
        deniedPermissions.remove(permission);
    }

    /**
     * 拒绝指定权限。此后对 {@link #has} 的调用将返回 false。
     */
    public void deny(@NonNull YoBluetoothPermission permission) {
        deniedPermissions.add(permission);
        grantedPermissions.remove(permission);
    }

    /**
     * 检查给定权限是否已授予。若已 {@link #grant 授予} 且未 {@link #deny 拒绝} 则视为已授予。
     */
    public boolean has(@NonNull YoBluetoothPermission permission) {
        if (deniedPermissions.contains(permission)) return false;
        return grantedPermissions.contains(permission);
    }

    /** 检查给定权限是否全部已授予。 */
    public boolean hasAll(@NonNull YoBluetoothPermission first,
                          @NonNull YoBluetoothPermission... rest) {
        if (!has(first)) return false;
        for (YoBluetoothPermission p : rest) {
            if (!has(p)) return false;
        }
        return true;
    }

    /** 重置所有授予与拒绝状态。 */
    public void reset() {
        grantedPermissions.clear();
        deniedPermissions.clear();
    }

    /** 返回当前已授予权限集合的副本。 */
    @NonNull
    public Set<YoBluetoothPermission> grantedPermissions() {
        return EnumSet.copyOf(grantedPermissions);
    }

    /** 返回当前已拒绝权限集合的副本。 */
    @NonNull
    public Set<YoBluetoothPermission> deniedPermissions() {
        return EnumSet.copyOf(deniedPermissions);
    }

    @NonNull
    @Override
    public String toString() {
        return "FakePermissionChecker{"
                + "granted=" + grantedPermissions
                + ", denied=" + deniedPermissions + "}";
    }
}
