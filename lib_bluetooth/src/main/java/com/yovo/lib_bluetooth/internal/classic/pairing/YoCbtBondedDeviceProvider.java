package com.yovo.lib_bluetooth.internal.classic.pairing;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 已配对设备提供者，从平台与快照存储组装 Bond 设备列表。
 */
public final class YoCbtBondedDeviceProvider {

    private static final String TAG = "YoCbtBondedDeviceProvider";

    private final YoBluetoothAdapterProvider adapterProvider;
    private final YoBluetoothPermissionChecker permissionChecker;
    private final YoCbtBondedDeviceCoordinator coordinator;

    public YoCbtBondedDeviceProvider(
            @NonNull YoBluetoothAdapterProvider adapterProvider,
            @NonNull YoBluetoothPermissionChecker permissionChecker,
            @NonNull YoCbtBondedDeviceCoordinator coordinator) {
        this.adapterProvider = adapterProvider;
        this.permissionChecker = permissionChecker;
        this.coordinator = coordinator;
    }

    @NonNull
    public YoResult<List<YoCbtDeviceSnapshot>> getBondedDevices(@NonNull Context context) {
        YoResult<Void> guard = requireConnect(context);
        if (guard.isFailure()) {
            return YoResult.failure(guard.getFailure());
        }
        return YoResult.success(coordinator.syncAll(context));
    }

    @NonNull
    public YoResult<YoCbtDeviceSnapshot> getBondedDevice(
            @NonNull Context context,
            @NonNull YoCbtDeviceId deviceId) {
        YoResult<Void> guard = requireConnect(context);
        if (guard.isFailure()) {
            return YoResult.failure(guard.getFailure());
        }
        YoCbtDeviceSnapshot snapshot = coordinator.syncOne(context, deviceId);
        if (snapshot == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BONDED_DEVICE_NOT_FOUND,
                    "Bonded device not found for device ID: " + deviceId,
                    TAG));
        }
        return YoResult.success(snapshot);
    }

    @NonNull
    private YoResult<Void> requireConnect(@NonNull Context context) {
        if (!permissionChecker.has(context, YoBluetoothPermission.CONNECT)) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED,
                    "BLUETOOTH_CONNECT permission required to query bonded devices",
                    TAG));
        }
        if (adapterProvider.adapter() == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BLUETOOTH_NOT_SUPPORTED,
                    "Bluetooth adapter is not available on this device",
                    TAG));
        }
        return YoResult.success(null);
    }
}
