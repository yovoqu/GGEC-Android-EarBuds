package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * GATT 读写目标。
 */
public final class BleGattTarget {

    @NonNull
    private final UUID serviceUuid;
    @NonNull
    private final UUID characteristicUuid;
    @Nullable
    private final UUID descriptorUuid;

    private BleGattTarget(@NonNull UUID serviceUuid,
                          @NonNull UUID characteristicUuid,
                          @Nullable UUID descriptorUuid) {
        this.serviceUuid = serviceUuid;
        this.characteristicUuid = characteristicUuid;
        this.descriptorUuid = descriptorUuid;
    }

    public static BleGattTarget characteristic(@NonNull UUID serviceUuid,
                                               @NonNull UUID characteristicUuid) {
        return new BleGattTarget(serviceUuid, characteristicUuid, null);
    }

    public static BleGattTarget descriptor(@NonNull UUID serviceUuid,
                                           @NonNull UUID characteristicUuid,
                                           @NonNull UUID descriptorUuid) {
        return new BleGattTarget(serviceUuid, characteristicUuid, descriptorUuid);
    }

    @NonNull
    public UUID serviceUuid() {
        return serviceUuid;
    }

    @NonNull
    public UUID characteristicUuid() {
        return characteristicUuid;
    }

    @Nullable
    public UUID descriptorUuid() {
        return descriptorUuid;
    }
}
