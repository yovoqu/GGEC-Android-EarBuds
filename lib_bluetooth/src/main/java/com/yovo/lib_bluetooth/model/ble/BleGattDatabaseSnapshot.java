package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 远端设备完整不可变 GATT 服务数据库快照。
 */
public final class BleGattDatabaseSnapshot {

    public static final UUID GENERIC_ATTRIBUTE_SERVICE_UUID =
            UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
    public static final UUID SERVICE_CHANGED_CHARACTERISTIC_UUID =
            UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb");
    public static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    @NonNull
    private final BleDeviceId deviceId;
    @NonNull
    private final List<BleGattServiceInfo> services;
    private final long discoveredAtMillis;

    public BleGattDatabaseSnapshot(@NonNull BleDeviceId deviceId,
                                   @NonNull List<BleGattServiceInfo> services,
                                   long discoveredAtMillis) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.services = Collections.unmodifiableList(new ArrayList<>(services));
        this.discoveredAtMillis = discoveredAtMillis;
    }

    @NonNull
    public BleDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public List<BleGattServiceInfo> services() {
        return services;
    }

    public long discoveredAtMillis() {
        return discoveredAtMillis;
    }

    @Nullable
    public BleGattServiceInfo findService(@NonNull UUID serviceUuid) {
        return findService(serviceUuid, BleGattTarget.UNSPECIFIED_INSTANCE_ID);
    }

    @Nullable
    public BleGattServiceInfo findService(@NonNull UUID serviceUuid, int serviceInstanceId) {
        for (BleGattServiceInfo service : services) {
            if (service.uuid().equals(serviceUuid)
                    && (serviceInstanceId < 0 || service.instanceId() == serviceInstanceId)) {
                return service;
            }
        }
        return null;
    }

    @Nullable
    public BleGattCharacteristicInfo findCharacteristic(@NonNull BleGattTarget target) {
        BleGattServiceInfo service = findService(target.serviceUuid(), target.serviceInstanceId());
        if (service == null) {
            return null;
        }
        return service.findCharacteristic(
                target.characteristicUuid(), target.characteristicInstanceId());
    }

    @Nullable
    public BleGattDescriptorInfo findDescriptor(@NonNull BleGattTarget target) {
        if (target.descriptorUuid() == null) {
            return null;
        }
        BleGattCharacteristicInfo characteristic = findCharacteristic(target);
        return characteristic == null ? null : characteristic.findDescriptor(target.descriptorUuid());
    }

    public boolean hasServiceChangedCharacteristic() {
        return findCharacteristic(BleGattTarget.characteristic(
                GENERIC_ATTRIBUTE_SERVICE_UUID, SERVICE_CHANGED_CHARACTERISTIC_UUID)) != null;
    }
}
