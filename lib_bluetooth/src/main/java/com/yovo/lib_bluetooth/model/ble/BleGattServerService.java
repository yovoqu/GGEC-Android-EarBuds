package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 本地托管 GATT Server 服务定义。
 */
public final class BleGattServerService {

    @NonNull
    private final UUID uuid;
    private final boolean primary;
    @NonNull
    private final List<BleGattServerCharacteristic> characteristics;

    private BleGattServerService(Builder builder) {
        this.uuid = builder.uuid;
        this.primary = builder.primary;
        this.characteristics = Collections.unmodifiableList(new ArrayList<>(builder.characteristics));
    }

    @NonNull
    public UUID uuid() {
        return uuid;
    }

    public boolean isPrimary() {
        return primary;
    }

    @NonNull
    public List<BleGattServerCharacteristic> characteristics() {
        return characteristics;
    }

    @NonNull
    public static Builder builder(@NonNull UUID uuid) {
        return new Builder(uuid);
    }

    public static final class Builder {
        @NonNull
        private final UUID uuid;
        private boolean primary = true;
        private final List<BleGattServerCharacteristic> characteristics = new ArrayList<>();

        private Builder(@NonNull UUID uuid) {
            this.uuid = Objects.requireNonNull(uuid, "uuid");
        }

        @NonNull
        public Builder primary(boolean primary) {
            this.primary = primary;
            return this;
        }

        @NonNull
        public Builder addCharacteristic(@NonNull BleGattServerCharacteristic characteristic) {
            this.characteristics.add(Objects.requireNonNull(characteristic, "characteristic"));
            return this;
        }

        @NonNull
        public BleGattServerService build() {
            return new BleGattServerService(this);
        }
    }
}
