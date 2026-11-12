package com.ggec.earbuds.provider.lib_common.permission;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.scan.ScanPermissionRequest;
import com.ggec.earbuds.ui.device.scan.ScanPermissionResult;

/**
 * 用 {@code lib_common} 权限域实现宿主页声明的权限能力缝。
 *
 * <p>{@link ScanPermissionRequest} 是 {@code ui/device/scan} 自有的宿主接口（Activity 侧声明）；
 * 本类是它在权限域上的唯一实现，由装配根按宿主构造并注入，不持有 Activity 生命周期外的引用。</p>
 *
 * <p>对 {@code lib_common} 的调用单点是 {@link PermissionProvider}：本类只把它的结果投影成
 * 界面约定的 {@link ScanPermissionResult}，不重复写一遍权限 API 调用。</p>
 */
public final class BluetoothScanPermissionRequest implements ScanPermissionRequest {

    private final Activity host;

    public BluetoothScanPermissionRequest(@NonNull Activity host) {
        this.host = host;
    }

    @Override
    public boolean ensureGranted() {
        return PermissionProvider.requestBluetoothIfNeeded(host);
    }

    @NonNull
    @Override
    public ScanPermissionResult onResult(int requestCode,
                                         @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        PermissionProvider.Result result = PermissionProvider.onRequestResult(
                host, requestCode, permissions, grantResults);
        return new ScanPermissionResult(
                result.handled(), result.allGranted(), result.permanentlyDenied());
    }
}
