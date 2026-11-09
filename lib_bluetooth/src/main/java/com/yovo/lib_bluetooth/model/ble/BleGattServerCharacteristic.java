package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 本地托管 GATT Server 特征定义（含 Shared / Per-Client 隔离策略与初始值）。
 */
public final class BleGattServerCharacteristic {

    @NonNull
    private final UUID uuid;
    private final int properties;
    private final int permissions;
    @NonNull
    private final BleCharacteristicScope scope;
    @NonNull
    private final byte[] initialValue;
    @NonNull
    private final List<BleGattServerDescriptor> descriptors;

    private BleGattServerCharacteristic(Builder builder) {
        this.uuid = builder.uuid;
        this.properties = builder.properties;
        this.permissions = builder.permissions;
        this.scope = builder.scope;
        this.initialValue = builder.initialValue.clone();
        this.descriptors = Collections.unmodifiableList(new ArrayList<>(builder.descriptors));
    }

    @NonNull
    public UUID uuid() {
        return uuid;
    }

    public int properties() {
        return properties;
    }

    public int permissions() {
        return permissions;
    }

    @NonNull
    public BleCharacteristicScope scope() {
        return scope;
    }

    @NonNull
    public byte[] initialValue() {
        return initialValue.clone();
    }

    @NonNull
    public List<BleGattServerDescriptor> descriptors() {
        return descriptors;
    }

    @NonNull
    public static Builder builder(@NonNull UUID uuid, int properties, int permissions) {
        return new Builder(uuid, properties, permissions);
    }

    public static final class Builder {
        @NonNull
        private final UUID uuid;
        private final int properties;
        private final int permissions;
        private BleCharacteristicScope scope = BleCharacteristicScope.PER_CLIENT;
        private byte[] initialValue = new byte[0];
        private final List<BleGattServerDescriptor> descriptors = new ArrayList<>();

        private Builder(@NonNull UUID uuid, int properties, int permissions) {
            this.uuid = Objects.requireNonNull(uuid, "uuid");
            this.properties = properties;
            this.permissions = permissions;
        }

        @NonNull
        public Builder scope(@NonNull BleCharacteristicScope scope) {
            this.scope = Objects.requireNonNull(scope, "scope");
            return this;
        }

        @NonNull
        public Builder initialValue(@Nullable byte[] initialValue) {
            this.initialValue = initialValue == null ? new byte[0] : initialValue.clone();
            return this;
        }

        @NonNull
        public Builder addDescriptor(@NonNull BleGattServerDescriptor descriptor) {
            this.descriptors.add(Objects.requireNonNull(descriptor, "descriptor"));
            return this;
        }

        @NonNull
        public Builder withCccd() {
            return addDescriptor(BleGattServerDescriptor.cccd());
        }

        @NonNull
        public BleGattServerCharacteristic build() {
            return new BleGattServerCharacteristic(this);
        }
    }
}
