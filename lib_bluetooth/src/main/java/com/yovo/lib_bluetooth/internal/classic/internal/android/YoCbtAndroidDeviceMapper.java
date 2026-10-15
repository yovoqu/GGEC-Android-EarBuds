package com.yovo.lib_bluetooth.internal.classic.internal.android;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 {@link BluetoothDevice} 等 Android 对象映射为 SDK 设备领域模型。
 */

@SuppressLint("MissingPermission")
public final class YoCbtAndroidDeviceMapper {
    @Nullable
    public YoCbtDeviceIdentity map(@Nullable BluetoothDevice device, @Nullable String overrideName) {
        if (device == null) return null;
        String address;
        try {
            address = device.getAddress();
        } catch (Throwable ignored) {
            address = "";
        }
        if (address == null || address.trim().isEmpty()) return null;
        String name = overrideName;
        if (name == null || name.trim().isEmpty()) {
            try {
                name = device.getName();
            } catch (Throwable ignored) {
                name = "";
            }
        }
        return new YoCbtDeviceIdentity(
                YoCbtDeviceId.fromAddress(address),
                address.trim().toUpperCase(java.util.Locale.US),
                YoCbtAddressType.CLASSIC,
                mapType(device),
                name);
    }

    @NonNull
    public YoCbtDeviceClass mapClass(@Nullable BluetoothClass bluetoothClass) {
        if (bluetoothClass == null) return YoCbtDeviceClass.unknown();
        return new YoCbtDeviceClass(bluetoothClass.hashCode(),
                bluetoothClass.getMajorDeviceClass(),
                bluetoothClass.getDeviceClass());
    }

    @NonNull
    private static YoCbtDeviceType mapType(BluetoothDevice device) {
        try {
            int type = device.getType();
            if (type == BluetoothDevice.DEVICE_TYPE_CLASSIC) return YoCbtDeviceType.CLASSIC;
            if (type == BluetoothDevice.DEVICE_TYPE_LE) return YoCbtDeviceType.LE;
            if (type == BluetoothDevice.DEVICE_TYPE_DUAL) return YoCbtDeviceType.DUAL;
        } catch (Throwable ignored) {
        }
        return YoCbtDeviceType.UNKNOWN;
    }
}
