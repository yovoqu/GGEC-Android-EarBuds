package com.yovo.lib_bluetooth.internal.ble.platform.gatt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Android GATT 服务树 → {@link BleGattDatabaseSnapshot} 的唯一映射点（含递归的 included services）。
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidGattSnapshotMapper {

    private YoBleAndroidGattSnapshotMapper() {
    }

    @NonNull
    public static BleGattDatabaseSnapshot capture(@NonNull BleDeviceId deviceId,
                                                   @NonNull BluetoothGatt gatt,
                                                   long timestampMillis) {
        List<BluetoothGattService> rawServices = gatt.getServices();
        List<BleGattServiceInfo> converted = new ArrayList<>(rawServices.size());
        for (BluetoothGattService rawService : rawServices) {
            converted.add(convertService(rawService));
        }
        return new BleGattDatabaseSnapshot(deviceId, converted, timestampMillis);
    }

    @NonNull
    private static BleGattServiceInfo convertService(@NonNull BluetoothGattService rawService) {
        List<BluetoothGattCharacteristic> rawCharacteristics = rawService.getCharacteristics();
        List<BleGattCharacteristicInfo> characteristics =
                new ArrayList<>(rawCharacteristics.size());
        for (BluetoothGattCharacteristic rawCharacteristic : rawCharacteristics) {
            List<BluetoothGattDescriptor> rawDescriptors = rawCharacteristic.getDescriptors();
            List<BleGattDescriptorInfo> descriptors = new ArrayList<>(rawDescriptors.size());
            for (BluetoothGattDescriptor rawDescriptor : rawDescriptors) {
                descriptors.add(new BleGattDescriptorInfo(
                        rawDescriptor.getUuid(), rawDescriptor.getPermissions()));
            }
            characteristics.add(new BleGattCharacteristicInfo(
                    rawCharacteristic.getUuid(),
                    rawCharacteristic.getInstanceId(),
                    rawCharacteristic.getProperties(),
                    rawCharacteristic.getPermissions(),
                    descriptors));
        }
        List<BluetoothGattService> rawIncluded = rawService.getIncludedServices();
        List<BleGattServiceInfo> included = new ArrayList<>(rawIncluded.size());
        for (BluetoothGattService service : rawIncluded) {
            included.add(convertService(service));
        }
        return new BleGattServiceInfo(
                rawService.getUuid(),
                rawService.getInstanceId(),
                rawService.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY,
                characteristics,
                included);
    }
}
