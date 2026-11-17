package com.yovo.lib_bluetooth.internal.classic.platform;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.Locale;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 {@link BluetoothDevice} 等 Android 对象映射为 SDK 设备领域模型。
 */

@SuppressLint("MissingPermission")
public final class YoCbtAndroidDeviceMapper {

    private static final String TAG = "YoCbtAndroidDevice";

    @Nullable
    public YoCbtDeviceIdentity map(@Nullable BluetoothDevice device, @Nullable String overrideName) {
        if (device == null) return null;
        String address = device.getAddress();
        if (address == null || address.trim().isEmpty()) return null;
        String name = overrideName;
        if (name == null || name.trim().isEmpty()) {
            name = readName(device);
        }
        return new YoCbtDeviceIdentity(
                YoCbtDeviceId.fromAddress(address),
                address.trim().toUpperCase(Locale.US),
                YoCbtAddressType.CLASSIC,
                mapType(device),
                name);
    }

    /**
     * GAP 名称需要 {@code BLUETOOTH_CONNECT}：权限被撤销时系统抛 {@link SecurityException}，
     * 名称按“无名称”呈现，地址/类型仍可映射。
     */
    @NonNull
    private static String readName(@NonNull BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null ? "" : name;
        } catch (SecurityException denied) {
            YoBtLogger.w(TAG, "BluetoothDevice.getName() denied by permission", denied);
            return "";
        }
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
        switch (device.getType()) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                return YoCbtDeviceType.CLASSIC;
            case BluetoothDevice.DEVICE_TYPE_LE:
                return YoCbtDeviceType.LE;
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                return YoCbtDeviceType.DUAL;
            default:
                return YoCbtDeviceType.UNKNOWN;
        }
    }
}
