package com.yovo.lib_bluetooth.internal.ble.platform.gatt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleGattTarget;

import java.util.UUID;

/**
 * {@link BleGattTarget} 与 Android GATT 对象树之间的双向寻址。
 *
 * <p>同一 UUID 可以出现多次（多服务实例 / 多特征实例），因此寻址必须带上
 * {@code serviceInstanceId} + {@code characteristicInstanceId}；实例 id 为负表示“不区分实例”。</p>
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidGattAddressing {

    private YoBleAndroidGattAddressing() {
    }

    @Nullable
    public static BluetoothGattService findService(@NonNull BluetoothGatt gatt,
                                                    @NonNull UUID serviceUuid,
                                                    int serviceInstanceId) {
        if (serviceInstanceId < 0) {
            return gatt.getService(serviceUuid);
        }
        for (BluetoothGattService service : gatt.getServices()) {
            if (serviceUuid.equals(service.getUuid())
                    && service.getInstanceId() == serviceInstanceId) {
                return service;
            }
        }
        return null;
    }

    @Nullable
    public static BluetoothGattCharacteristic findCharacteristic(@NonNull BluetoothGatt gatt,
                                                                  @NonNull BleGattTarget target) {
        BluetoothGattService service = findService(
                gatt, target.serviceUuid(), target.serviceInstanceId());
        if (service == null) {
            return null;
        }
        if (target.characteristicInstanceId() < 0) {
            return service.getCharacteristic(target.characteristicUuid());
        }
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            if (target.characteristicUuid().equals(characteristic.getUuid())
                    && characteristic.getInstanceId() == target.characteristicInstanceId()) {
                return characteristic;
            }
        }
        return null;
    }

    @Nullable
    public static BluetoothGattDescriptor findDescriptor(@NonNull BluetoothGatt gatt,
                                                          @NonNull BleGattTarget target) {
        if (target.descriptorUuid() == null) {
            return null;
        }
        BluetoothGattCharacteristic characteristic = findCharacteristic(gatt, target);
        return characteristic == null ? null : characteristic.getDescriptor(target.descriptorUuid());
    }

    @NonNull
    public static BleGattTarget targetForCharacteristic(
            @NonNull BluetoothGattCharacteristic characteristic) {
        BluetoothGattService service = characteristic.getService();
        if (service == null) {
            return BleGattTarget.characteristic(new UUID(0L, 0L), characteristic.getUuid());
        }
        return BleGattTarget.characteristic(
                service.getUuid(),
                service.getInstanceId(),
                characteristic.getUuid(),
                characteristic.getInstanceId());
    }

    @NonNull
    public static BleGattTarget targetForDescriptor(@NonNull BluetoothGattDescriptor descriptor) {
        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        if (characteristic == null || characteristic.getService() == null) {
            return BleGattTarget.descriptor(new UUID(0L, 0L), new UUID(0L, 0L), descriptor.getUuid());
        }
        BluetoothGattService service = characteristic.getService();
        return BleGattTarget.descriptor(
                service.getUuid(),
                service.getInstanceId(),
                characteristic.getUuid(),
                characteristic.getInstanceId(),
                descriptor.getUuid());
    }
}
