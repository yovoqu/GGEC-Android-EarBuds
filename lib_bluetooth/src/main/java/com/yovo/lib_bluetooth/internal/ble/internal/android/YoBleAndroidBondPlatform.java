package com.yovo.lib_bluetooth.internal.ble.internal.android;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.internal.android.compat.YoBleScanCompat;
import com.yovo.lib_bluetooth.internal.ble.internal.android.hiddenapi.YoBleBondReflection;
import com.yovo.lib_bluetooth.internal.ble.internal.android.hiddenapi.YoBleHiddenApiPolicy;
import com.yovo.lib_bluetooth.internal.ble.internal.android.receiver.YoBleBondStateReceiver;
import com.yovo.lib_bluetooth.internal.ble.pairing.YoBleBondPlatform;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleDeviceType;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Android BLE Bond 平台适配实现（屏蔽权限与反射异常边界）。
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidBondPlatform implements YoBleBondPlatform {

    private final YoBluetoothAdapterProvider adapterProvider;

    public YoBleAndroidBondPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider) {
        this.adapterProvider = adapterProvider;
    }

    @NonNull
    @Override
    public List<BleDeviceIdentity> getBondedDevices() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            return Collections.emptyList();
        }
        Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
        if (bonded == null || bonded.isEmpty()) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        List<BleDeviceIdentity> out = new ArrayList<>();
        for (BluetoothDevice d : bonded) {
            if (d == null || !BleDeviceId.isValid(d.getAddress())) {
                continue;
            }
            BleDeviceType type = YoBleScanCompat.resolveDeviceType(d);
            if (type == BleDeviceType.CLASSIC) {
                continue; // 仅筛选 BLE / Dual / Unknown
            }
            // 配对列表没有广播包，不能把 getName()（经典/缓存名）写成 BLE 广播名。
            String name = "";
            String alias = null;
            if (AndroidPlatform.supportsDeviceAlias()) {
                try {
                    alias = d.getAlias();
                } catch (Throwable ignored) {
                }
            }
            out.add(new BleDeviceIdentity(
                    new BleDeviceId(d.getAddress()),
                    name,
                    alias,
                    YoBleScanCompat.resolveAddressType(d),
                    type,
                    now));
        }
        return out;
    }

    @NonNull
    @Override
    public BleBondState getBondState(@NonNull BleDeviceId deviceId) {
        BluetoothDevice dev = resolve(deviceId);
        if (dev == null) {
            return BleBondState.UNKNOWN;
        }
        try {
            return YoBleBondStateReceiver.mapBondState(dev.getBondState());
        } catch (Throwable t) {
            return BleBondState.UNKNOWN;
        }
    }

    @NonNull
    @Override
    public ActionResult createBond(@NonNull BleDeviceId deviceId) {
        BluetoothDevice dev = resolve(deviceId);
        if (dev == null) {
            return ActionResult.fail(BleErrorCode.DEVICE_INVALID, "Invalid device MAC");
        }
        try {
            boolean ok = dev.createBond();
            return ok ? ActionResult.ok()
                    : ActionResult.fail(BleErrorCode.BOND_FAILED, "BluetoothDevice.createBond() returned false");
        } catch (Throwable t) {
            return ActionResult.fail(BleErrorCode.BOND_FAILED,
                    "BluetoothDevice.createBond() threw: " + t.getMessage());
        }
    }

    @NonNull
    @Override
    public ActionResult cancelBondProcess(@NonNull BleDeviceId deviceId) {
        BluetoothDevice dev = resolve(deviceId);
        if (dev == null) {
            return ActionResult.fail(BleErrorCode.DEVICE_INVALID, "Invalid device MAC");
        }
        boolean ok = YoBleBondReflection.cancelBondProcess(dev);
        return ok ? ActionResult.ok()
                : ActionResult.fail(BleErrorCode.BOND_CANCELLED, "cancelBondProcess failed or unsupported");
    }

    @NonNull
    @Override
    public ActionResult removeBond(@NonNull BleDeviceId deviceId, boolean allowHiddenApi) {
        if (!allowHiddenApi) {
            return ActionResult.fail(BleErrorCode.HIDDEN_API_BLOCKED, "Hidden API removeBond disabled");
        }
        BluetoothDevice dev = resolve(deviceId);
        if (dev == null) {
            return ActionResult.fail(BleErrorCode.DEVICE_INVALID, "Invalid device MAC");
        }
        boolean ok = YoBleBondReflection.removeBond(dev, YoBleHiddenApiPolicy.EXPLICITLY_ENABLED);
        return ok ? ActionResult.ok()
                : ActionResult.fail(BleErrorCode.HIDDEN_API_FAILED, "removeBond returned false");
    }

    @Nullable
    private BluetoothDevice resolve(@NonNull BleDeviceId deviceId) {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            return null;
        }
        try {
            return adapter.getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
