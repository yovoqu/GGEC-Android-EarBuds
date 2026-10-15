package com.yovo.lib_bluetooth.internal.classic.internal.android.pairing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi.YoCbtHiddenApiBondManager;
import com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi.YoCbtHiddenApiPolicy;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondOperationPort;

import java.io.IOException;
import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android 配对/解除配对操作平台实现。
 */

public final class YoCbtAndroidBondOperationPlatform implements YoCbtBondOperationPort {

    private static final String TAG = "YoCbtAndroidBondOperation";

    private final YoBluetoothAdapterProvider adapterProvider;
    private final boolean allowHiddenApi;

    public YoCbtAndroidBondOperationPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider,
                                              boolean allowHiddenApi) {
        this.adapterProvider = Objects.requireNonNull(adapterProvider);
        this.allowHiddenApi = allowHiddenApi;
    }

    @NonNull
    @Override
    public YoResult<Void> createBond(@NonNull Context context, @NonNull YoCbtDeviceId deviceId) {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BLUETOOTH_NOT_SUPPORTED,
                    "Bluetooth adapter is not available on this device",
                    TAG));
        }

        BluetoothDevice device = resolveDevice(adapter, deviceId);
        if (device == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BONDED_DEVICE_NOT_FOUND,
                    "Cannot resolve BluetoothDevice from device ID: " + deviceId,
                    TAG));
        }

        try {
            if (!device.createBond()) {
                return YoResult.failure(YoCbtFailure.of(
                        YoCbtErrorCode.YO_CBT_BOND_START_FAILED,
                        "createBond returned false for device: " + deviceId,
                        TAG));
            }
            return YoResult.success(null);
        } catch (SecurityException e) {
            return YoResult.failure(new YoCbtFailure(
                    YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED,
                    "Security exception during createBond: " + e.getMessage(),
                    e, false, false, TAG,
                    deviceId, null, null, null,
                    "Grant BLUETOOTH_CONNECT permission at runtime"));
        }
    }

    @NonNull
    @Override
    public YoResult<Void> cancelBondProcess(@NonNull YoCbtDeviceId deviceId) {
        if (!allowHiddenApi) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BOND_HIDDEN_API_BLOCKED,
                    "Hidden API is not enabled; cannot cancel bond process for: " + deviceId,
                    TAG));
        }

        BluetoothDevice device = requireDevice(deviceId);
        if (device == null) {
            return deviceNotFound(deviceId);
        }

        try {
            if (!YoCbtHiddenApiBondManager.cancelBondProcess(
                    device, YoCbtHiddenApiPolicy.EXPLICITLY_ENABLED)) {
                return YoResult.failure(YoCbtFailure.of(
                        YoCbtErrorCode.YO_CBT_BOND_CANCEL_FAILED,
                        "cancelBondProcess returned false for device: " + deviceId,
                        TAG));
            }
            return YoResult.success(null);
        } catch (IOException e) {
            return hiddenApiFailure(YoCbtErrorCode.YO_CBT_BOND_CANCEL_FAILED,
                    "Hidden API cancelBondProcess failed: " + e.getMessage(), deviceId, e);
        }
    }

    @NonNull
    @Override
    public YoResult<Void> removeBond(@NonNull YoCbtDeviceId deviceId) {
        if (!allowHiddenApi) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BOND_HIDDEN_API_BLOCKED,
                    "Hidden API is not enabled; cannot remove bond for: " + deviceId,
                    TAG));
        }

        BluetoothDevice device = requireDevice(deviceId);
        if (device == null) {
            return deviceNotFound(deviceId);
        }

        try {
            if (!YoCbtHiddenApiBondManager.removeBond(
                    device, YoCbtHiddenApiPolicy.EXPLICITLY_ENABLED)) {
                return YoResult.failure(YoCbtFailure.of(
                        YoCbtErrorCode.YO_CBT_BOND_REMOVE_FAILED,
                        "removeBond returned false for device: " + deviceId,
                        TAG));
            }
            return YoResult.success(null);
        } catch (IOException e) {
            return hiddenApiFailure(YoCbtErrorCode.YO_CBT_BOND_REMOVE_FAILED,
                    "Hidden API removeBond failed: " + e.getMessage(), deviceId, e);
        }
    }

    @Nullable
    private BluetoothDevice requireDevice(@NonNull YoCbtDeviceId deviceId) {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) return null;
        return resolveDevice(adapter, deviceId);
    }

    @NonNull
    private static YoResult<Void> deviceNotFound(@NonNull YoCbtDeviceId deviceId) {
        return YoResult.failure(YoCbtFailure.of(
                YoCbtErrorCode.YO_CBT_BONDED_DEVICE_NOT_FOUND,
                "Cannot resolve BluetoothDevice from device ID: " + deviceId,
                TAG));
    }

    @NonNull
    private static YoResult<Void> hiddenApiFailure(@NonNull YoCbtErrorCode code,
                                                   @NonNull String message,
                                                   @NonNull YoCbtDeviceId deviceId,
                                                   @NonNull IOException cause) {
        return YoResult.failure(new YoCbtFailure(
                code, message, cause, true, true, TAG,
                deviceId, null, null, null,
                "Check that hidden API access is granted via platform permissions"));
    }

    @Nullable
    private static BluetoothDevice resolveDevice(@NonNull BluetoothAdapter adapter,
                                                 @NonNull YoCbtDeviceId deviceId) {
        try {
            return adapter.getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
