package com.yovo.lib_bluetooth.internal.classic.pairing;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Bond 操作控制器，编排 create/cancel/remove 配对流程。
 */
public final class YoCbtBondOperationController {

    private static final String TAG = "YoCbtBondOperationController";

    private final YoBluetoothPermissionChecker permissionChecker;
    private final YoCbtBondOperationPort bondOperationPort;

    public YoCbtBondOperationController(
            @NonNull YoBluetoothPermissionChecker permissionChecker,
            @NonNull YoCbtBondOperationPort bondOperationPort) {
        this.permissionChecker = Objects.requireNonNull(permissionChecker);
        this.bondOperationPort = Objects.requireNonNull(bondOperationPort);
    }

    @NonNull
    public YoResult<Void> createBond(@NonNull Context context, @NonNull YoCbtDeviceId deviceId) {
        if (!permissionChecker.has(context, YoBluetoothPermission.CONNECT)) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED,
                    "BLUETOOTH_CONNECT permission required to create bond",
                    TAG));
        }
        return bondOperationPort.createBond(context, deviceId);
    }

    @NonNull
    public YoResult<Void> cancelBondProcess(@NonNull YoCbtDeviceId deviceId) {
        return bondOperationPort.cancelBondProcess(deviceId);
    }

    @NonNull
    public YoResult<Void> removeBond(@NonNull YoCbtDeviceId deviceId) {
        return bondOperationPort.removeBond(deviceId);
    }
}
