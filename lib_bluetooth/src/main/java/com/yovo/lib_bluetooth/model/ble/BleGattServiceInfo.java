package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 不可变 GATT 服务元数据快照。
 */
public final class BleGattServiceInfo {

    @NonNull
    private final UUID uuid;
    private final int instanceId;
    private final boolean primary;
    @NonNull
    private final List<BleGattCharacteristicInfo> characteristics;
    @NonNull
    private final List<BleGattServiceInfo> includedServices;

    public BleGattServiceInfo(@NonNull UUID uuid,
                              int instanceId,
                              boolean primary,
                              @NonNull List<BleGattCharacteristicInfo> characteristics,
                              @NonNull List<BleGattServiceInfo> includedServices) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.instanceId = instanceId;
        this.primary = primary;
        this.characteristics = Collections.unmodifiableList(new ArrayList<>(characteristics));
        this.includedServices = Collections.unmodifiableList(new ArrayList<>(includedServices));
    }

    @NonNull
    public UUID uuid() {
        return uuid;
    }

    public int instanceId() {
        return instanceId;
    }

    public boolean isPrimary() {
        return primary;
    }

    @NonNull
    public List<BleGattCharacteristicInfo> characteristics() {
        return characteristics;
    }

    @NonNull
    public List<BleGattServiceInfo> includedServices() {
        return includedServices;
    }

    @Nullable
    public BleGattCharacteristicInfo findCharacteristic(@NonNull UUID characteristicUuid) {
        return findCharacteristic(characteristicUuid, BleGattTarget.UNSPECIFIED_INSTANCE_ID);
    }

    @Nullable
    public BleGattCharacteristicInfo findCharacteristic(@NonNull UUID characteristicUuid,
                                                        int characteristicInstanceId) {
        for (BleGattCharacteristicInfo c : characteristics) {
            if (c.uuid().equals(characteristicUuid)
                    && (characteristicInstanceId < 0 || c.instanceId() == characteristicInstanceId)) {
                return c;
            }
        }
        return null;
    }
}
