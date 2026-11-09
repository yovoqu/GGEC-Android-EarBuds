package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 本地托管 GATT Server 描述符定义。
 */
public final class BleGattServerDescriptor {

    @NonNull
    private final UUID uuid;
    private final int permissions;
    @NonNull
    private final byte[] initialValue;

    public BleGattServerDescriptor(@NonNull UUID uuid,
                                   int permissions,
                                   @Nullable byte[] initialValue) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.permissions = permissions;
        this.initialValue = initialValue == null ? new byte[0] : initialValue.clone();
    }

    @NonNull
    public static BleGattServerDescriptor cccd() {
        return new BleGattServerDescriptor(
                BleGattDatabaseSnapshot.CCCD_UUID,
                BleGattDescriptorInfo.PERMISSION_READ | BleGattDescriptorInfo.PERMISSION_WRITE,
                new byte[]{0x00, 0x00});
    }

    @NonNull
    public UUID uuid() {
        return uuid;
    }

    public int permissions() {
        return permissions;
    }

    @NonNull
    public byte[] initialValue() {
        return initialValue.clone();
    }
}
