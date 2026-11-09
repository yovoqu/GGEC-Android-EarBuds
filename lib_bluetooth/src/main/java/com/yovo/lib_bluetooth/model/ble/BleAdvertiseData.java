package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * BLE 外设广播包 / 扫描响应载荷模型。
 */
public final class BleAdvertiseData {

    @NonNull
    private final List<UUID> serviceUuids;
    @NonNull
    private final List<UUID> serviceSolicitationUuids;
    @NonNull
    private final Map<UUID, byte[]> serviceData;
    @NonNull
    private final Map<Integer, byte[]> manufacturerData;
    private final boolean includeDeviceName;
    private final boolean includeTxPowerLevel;

    private BleAdvertiseData(Builder builder) {
        this.serviceUuids = Collections.unmodifiableList(new ArrayList<>(builder.serviceUuids));
        this.serviceSolicitationUuids = Collections.unmodifiableList(
                new ArrayList<>(builder.serviceSolicitationUuids));

        Map<UUID, byte[]> svc = new LinkedHashMap<>();
        for (Map.Entry<UUID, byte[]> entry : builder.serviceData.entrySet()) {
            svc.put(entry.getKey(), entry.getValue().clone());
        }
        this.serviceData = Collections.unmodifiableMap(svc);

        Map<Integer, byte[]> manu = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> entry : builder.manufacturerData.entrySet()) {
            manu.put(entry.getKey(), entry.getValue().clone());
        }
        this.manufacturerData = Collections.unmodifiableMap(manu);
        this.includeDeviceName = builder.includeDeviceName;
        this.includeTxPowerLevel = builder.includeTxPowerLevel;
    }

    @NonNull
    public List<UUID> serviceUuids() {
        return serviceUuids;
    }

    @NonNull
    public List<UUID> serviceSolicitationUuids() {
        return serviceSolicitationUuids;
    }

    @NonNull
    public Map<UUID, byte[]> serviceData() {
        Map<UUID, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, byte[]> entry : serviceData.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

    @NonNull
    public Map<Integer, byte[]> manufacturerData() {
        Map<Integer, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> entry : manufacturerData.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

    public boolean includeDeviceName() {
        return includeDeviceName;
    }

    public boolean includeTxPowerLevel() {
        return includeTxPowerLevel;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<UUID> serviceUuids = new ArrayList<>();
        private final List<UUID> serviceSolicitationUuids = new ArrayList<>();
        private final Map<UUID, byte[]> serviceData = new LinkedHashMap<>();
        private final Map<Integer, byte[]> manufacturerData = new LinkedHashMap<>();
        private boolean includeDeviceName = false;
        private boolean includeTxPowerLevel = false;

        @NonNull
        public Builder addServiceUuid(@NonNull UUID serviceUuid) {
            this.serviceUuids.add(Objects.requireNonNull(serviceUuid, "serviceUuid"));
            return this;
        }

        @NonNull
        public Builder addServiceSolicitationUuid(@NonNull UUID solicitationUuid) {
            this.serviceSolicitationUuids.add(
                    Objects.requireNonNull(solicitationUuid, "solicitationUuid"));
            return this;
        }

        @NonNull
        public Builder addServiceData(@NonNull UUID serviceUuid, @NonNull byte[] data) {
            this.serviceData.put(
                    Objects.requireNonNull(serviceUuid, "serviceUuid"),
                    Objects.requireNonNull(data, "data").clone());
            return this;
        }

        @NonNull
        public Builder addManufacturerData(int manufacturerId, @NonNull byte[] data) {
            if (manufacturerId < 0 || manufacturerId > 0xFFFF) {
                throw new IllegalArgumentException("Invalid manufacturerId: " + manufacturerId);
            }
            this.manufacturerData.put(manufacturerId,
                    Objects.requireNonNull(data, "data").clone());
            return this;
        }

        @NonNull
        public Builder includeDeviceName(boolean includeDeviceName) {
            this.includeDeviceName = includeDeviceName;
            return this;
        }

        @NonNull
        public Builder includeTxPowerLevel(boolean includeTxPowerLevel) {
            this.includeTxPowerLevel = includeTxPowerLevel;
            return this;
        }

        @NonNull
        public BleAdvertiseData build() {
            return new BleAdvertiseData(this);
        }
    }
}
