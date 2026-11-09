package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.UUID;

/**
 * 不可变 GATT 描述符元数据快照。
 */
public final class BleGattDescriptorInfo {

    public static final int PERMISSION_READ = 0x01;
    public static final int PERMISSION_READ_ENCRYPTED = 0x02;
    public static final int PERMISSION_READ_ENCRYPTED_MITM = 0x04;
    public static final int PERMISSION_WRITE = 0x10;
    public static final int PERMISSION_WRITE_ENCRYPTED = 0x20;
    public static final int PERMISSION_WRITE_ENCRYPTED_MITM = 0x40;
    public static final int PERMISSION_WRITE_SIGNED = 0x80;
    public static final int PERMISSION_WRITE_SIGNED_MITM = 0x100;

    @NonNull
    private final UUID uuid;
    private final int permissions;

    public BleGattDescriptorInfo(@NonNull UUID uuid, int permissions) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.permissions = permissions;
    }

    @NonNull
    public UUID uuid() {
        return uuid;
    }

    public int permissions() {
        return permissions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BleGattDescriptorInfo)) return false;
        BleGattDescriptorInfo that = (BleGattDescriptorInfo) o;
        return permissions == that.permissions && uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, permissions);
    }
}
