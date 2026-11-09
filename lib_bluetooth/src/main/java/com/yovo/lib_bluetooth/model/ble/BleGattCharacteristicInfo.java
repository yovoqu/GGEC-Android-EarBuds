package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 不可变 GATT 特征元数据快照，常量 bitmask 与 Bluetooth SIG Characteristic 属性对齐。
 */
public final class BleGattCharacteristicInfo {

    public static final int PROPERTY_BROADCAST = 0x01;
    public static final int PROPERTY_READ = 0x02;
    public static final int PROPERTY_WRITE_NO_RESPONSE = 0x04;
    public static final int PROPERTY_WRITE = 0x08;
    public static final int PROPERTY_NOTIFY = 0x10;
    public static final int PROPERTY_INDICATE = 0x20;
    public static final int PROPERTY_SIGNED_WRITE = 0x40;
    public static final int PROPERTY_EXTENDED_PROPS = 0x80;

    @NonNull
    private final UUID uuid;
    private final int instanceId;
    private final int properties;
    private final int permissions;
    @NonNull
    private final List<BleGattDescriptorInfo> descriptors;

    public BleGattCharacteristicInfo(@NonNull UUID uuid,
                                     int instanceId,
                                     int properties,
                                     int permissions,
                                     @NonNull List<BleGattDescriptorInfo> descriptors) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.instanceId = instanceId;
        this.properties = properties;
        this.permissions = permissions;
        this.descriptors = Collections.unmodifiableList(new ArrayList<>(descriptors));
    }

    @NonNull
    public UUID uuid() {
        return uuid;
    }

    public int instanceId() {
        return instanceId;
    }

    public int properties() {
        return properties;
    }

    public int permissions() {
        return permissions;
    }

    @NonNull
    public List<BleGattDescriptorInfo> descriptors() {
        return descriptors;
    }

    public boolean isReadable() {
        return (properties & PROPERTY_READ) != 0;
    }

    public boolean isWritableWithResponse() {
        return (properties & PROPERTY_WRITE) != 0;
    }

    public boolean isWritableWithoutResponse() {
        return (properties & PROPERTY_WRITE_NO_RESPONSE) != 0;
    }

    public boolean isSignedWritable() {
        return (properties & PROPERTY_SIGNED_WRITE) != 0;
    }

    public boolean isNotifiable() {
        return (properties & PROPERTY_NOTIFY) != 0;
    }

    public boolean isIndicatable() {
        return (properties & PROPERTY_INDICATE) != 0;
    }

    @Nullable
    public BleGattDescriptorInfo findDescriptor(@NonNull UUID descriptorUuid) {
        for (BleGattDescriptorInfo descriptor : descriptors) {
            if (descriptor.uuid().equals(descriptorUuid)) {
                return descriptor;
            }
        }
        return null;
    }
}
