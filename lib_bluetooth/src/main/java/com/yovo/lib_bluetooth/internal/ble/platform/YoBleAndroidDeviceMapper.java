package com.yovo.lib_bluetooth.internal.ble.platform;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;
import com.yovo.lib_bluetooth.model.ble.BleAddressType;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleDeviceType;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

/**
 * Android {@link BluetoothDevice} 的平台属性（地址类型 / 设备类型 / 系统别名）→ BLE 模型的唯一映射点。
 *
 * <p>扫描结果与配对列表都从这里取身份，避免同一属性在两处各写一份读取与降级规则。</p>
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidDeviceMapper {

    private static final String TAG = "YoBleDeviceMapper";

    private YoBleAndroidDeviceMapper() {
    }

    /**
     * 组装一次 BLE 观察身份。
     *
     * @param advertisingName 只接受广播 AD Local Name；配对列表等无广播包的场景传空串，
     *                        绝不把 {@code BluetoothDevice.getName()} 当作 LE 广播名。
     */
    @NonNull
    public static BleDeviceIdentity identity(@NonNull BluetoothDevice device,
                                             @NonNull BleDeviceId deviceId,
                                             @NonNull String advertisingName,
                                             long observedAtMillis) {
        return new BleDeviceIdentity(
                deviceId,
                advertisingName,
                readAlias(device),
                resolveAddressType(device),
                resolveDeviceType(device),
                observedAtMillis);
    }

    /**
     * 系统别名（用户为该设备起的名字）。别名是可选装饰：未授予 {@code BLUETOOTH_CONNECT} 时系统抛
     * {@link SecurityException}，此时身份里的别名按“无别名”呈现，不影响地址/类型/广播名。
     */
    @Nullable
    public static String readAlias(@NonNull BluetoothDevice device) {
        if (!AndroidPlatform.supportsDeviceAlias()) {
            return null;
        }
        try {
            return device.getAlias();
        } catch (SecurityException denied) {
            YoBtLogger.w(TAG, "BluetoothDevice.getAlias() denied by permission, alias omitted", denied);
            return null;
        }
    }

    @NonNull
    public static BleAddressType resolveAddressType(@NonNull BluetoothDevice device) {
        if (!AndroidPlatform.supportsDeviceAddressType()) {
            return BleAddressType.UNKNOWN;
        }
        switch (device.getAddressType()) {
            case BluetoothDevice.ADDRESS_TYPE_PUBLIC:
                return BleAddressType.PUBLIC;
            case BluetoothDevice.ADDRESS_TYPE_RANDOM:
                return BleAddressType.RANDOM_STATIC;
            default:
                // 平台未给出可判定的地址类型（含 ADDRESS_TYPE_UNKNOWN 与任何新增取值）。
                return BleAddressType.UNKNOWN;
        }
    }

    @NonNull
    public static BleDeviceType resolveDeviceType(@NonNull BluetoothDevice device) {
        switch (device.getType()) {
            case BluetoothDevice.DEVICE_TYPE_LE:
                return BleDeviceType.LE;
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                return BleDeviceType.DUAL;
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                return BleDeviceType.CLASSIC;
            default:
                return BleDeviceType.UNKNOWN;
        }
    }
}
