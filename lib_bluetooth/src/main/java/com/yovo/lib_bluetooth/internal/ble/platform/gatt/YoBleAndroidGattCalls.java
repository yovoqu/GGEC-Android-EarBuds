package com.yovo.lib_bluetooth.internal.ble.platform.gatt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

/**
 * 对 Android {@link BluetoothGatt} 的写/连接/优先级调用封装的唯一位置：把返回值差异（boolean 与
 * {@link BluetoothStatusCodes}）归一成统一语义。
 *
 * <p>特征/描述符的写入签名在 API 33 分叉：33 起为带值的
 * {@code writeCharacteristic(characteristic, value, writeType)} 并返回状态码，之前是先 set 再写并返回 boolean。
 * 分支只按 {@link AndroidPlatform#usesTypedGattValueCallbacks()} 选择一条路径。</p>
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidGattCalls {

    private YoBleAndroidGattCalls() {
    }

    @Nullable
    public static BluetoothGatt connectLe(@NonNull Context context,
                                          @NonNull BluetoothDevice device,
                                          boolean autoConnect,
                                          @NonNull BluetoothGattCallback callback) {
        return device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE);
    }

    @SuppressWarnings("deprecation")
    public static boolean writeCharacteristic(@NonNull BluetoothGatt gatt,
                                              @NonNull BluetoothGattCharacteristic characteristic,
                                              @NonNull byte[] value,
                                              @NonNull BleWriteType writeType) {
        int androidWriteType = toAndroidWriteType(writeType);
        if (AndroidPlatform.usesTypedGattValueCallbacks()) {
            return gatt.writeCharacteristic(characteristic, value, androidWriteType)
                    == BluetoothStatusCodes.SUCCESS;
        }
        characteristic.setWriteType(androidWriteType);
        characteristic.setValue(value);
        return gatt.writeCharacteristic(characteristic);
    }

    @SuppressWarnings("deprecation")
    public static boolean writeDescriptor(@NonNull BluetoothGatt gatt,
                                          @NonNull BluetoothGattDescriptor descriptor,
                                          @NonNull byte[] value) {
        if (AndroidPlatform.usesTypedGattValueCallbacks()) {
            return gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS;
        }
        descriptor.setValue(value);
        return gatt.writeDescriptor(descriptor);
    }

    public static int toAndroidWriteType(@NonNull BleWriteType writeType) {
        switch (writeType) {
            case WITHOUT_RESPONSE:
                return BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
            case SIGNED:
                return BluetoothGattCharacteristic.WRITE_TYPE_SIGNED;
            case WITH_RESPONSE:
                return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
            default:
                throw new IllegalStateException("unhandled BleWriteType: " + writeType);
        }
    }

    public static int toAndroidConnectionPriority(@NonNull BleConnectionPriority priority) {
        switch (priority) {
            case HIGH:
                return BluetoothGatt.CONNECTION_PRIORITY_HIGH;
            case LOW_POWER:
                return BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
            case DCK:
                if (AndroidPlatform.supportsDckConnectionPriority()) {
                    return BluetoothGatt.CONNECTION_PRIORITY_DCK;
                }
                // API 34 之前没有 DCK 优先级常量：按同等“最高吞吐”语义降级为 HIGH。
                return BluetoothGatt.CONNECTION_PRIORITY_HIGH;
            case BALANCED:
                return BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
            default:
                throw new IllegalStateException("unhandled BleConnectionPriority: " + priority);
        }
    }
}
