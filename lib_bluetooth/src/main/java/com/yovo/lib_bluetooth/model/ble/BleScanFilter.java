package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * BLE 多维扫描过滤器（单 Filter 内非空字段为 AND 语义；多个 Filter 之间为 OR 语义）。
 */
public final class BleScanFilter {

    @Nullable
    private final String deviceAddress;
    @Nullable
    private final String deviceName;
    @Nullable
    private final String deviceNameContains;
    @Nullable
    private final UUID serviceUuid;
    @Nullable
    private final UUID serviceUuidMask;
    @Nullable
    private final UUID solicitationUuid;
    @Nullable
    private final UUID solicitationUuidMask;
    @Nullable
    private final UUID serviceDataUuid;
    @Nullable
    private final byte[] serviceData;
    @Nullable
    private final byte[] serviceDataMask;
    @Nullable
    private final Integer manufacturerId;
    @Nullable
    private final byte[] manufacturerData;
    @Nullable
    private final byte[] manufacturerDataMask;
    @Nullable
    private final Integer minRssi;
    private final boolean requireConnectable;

    private BleScanFilter(Builder builder) {
        this.deviceAddress = builder.deviceAddress == null
                ? null : new BleDeviceId(builder.deviceAddress).value();
        this.deviceName = builder.deviceName;
        this.deviceNameContains = builder.deviceNameContains;
        this.serviceUuid = builder.serviceUuid;
        this.serviceUuidMask = builder.serviceUuidMask;
        this.solicitationUuid = builder.solicitationUuid;
        this.solicitationUuidMask = builder.solicitationUuidMask;
        this.serviceDataUuid = builder.serviceDataUuid;
        this.serviceData = copyOf(builder.serviceData);
        this.serviceDataMask = copyOf(builder.serviceDataMask);
        this.manufacturerId = builder.manufacturerId;
        this.manufacturerData = copyOf(builder.manufacturerData);
        this.manufacturerDataMask = copyOf(builder.manufacturerDataMask);
        this.minRssi = builder.minRssi;
        this.requireConnectable = builder.requireConnectable;
    }

    @Nullable
    public String deviceAddress() {
        return deviceAddress;
    }

    @Nullable
    public String deviceName() {
        return deviceName;
    }

    @Nullable
    public String deviceNameContains() {
        return deviceNameContains;
    }

    @Nullable
    public UUID serviceUuid() {
        return serviceUuid;
    }

    @Nullable
    public UUID serviceUuidMask() {
        return serviceUuidMask;
    }

    @Nullable
    public UUID solicitationUuid() {
        return solicitationUuid;
    }

    @Nullable
    public UUID solicitationUuidMask() {
        return solicitationUuidMask;
    }

    @Nullable
    public UUID serviceDataUuid() {
        return serviceDataUuid;
    }

    @Nullable
    public byte[] serviceData() {
        return copyOf(serviceData);
    }

    @Nullable
    public byte[] serviceDataMask() {
        return copyOf(serviceDataMask);
    }

    @Nullable
    public Integer manufacturerId() {
        return manufacturerId;
    }

    @Nullable
    public byte[] manufacturerData() {
        return copyOf(manufacturerData);
    }

    @Nullable
    public byte[] manufacturerDataMask() {
        return copyOf(manufacturerDataMask);
    }

    @Nullable
    public Integer minRssi() {
        return minRssi;
    }

    public boolean requireConnectable() {
        return requireConnectable;
    }

    public boolean isEmpty() {
        return deviceAddress == null
                && deviceName == null
                && deviceNameContains == null
                && serviceUuid == null
                && solicitationUuid == null
                && serviceDataUuid == null
                && manufacturerId == null
                && minRssi == null
                && !requireConnectable;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        @Nullable
        private String deviceAddress;
        @Nullable
        private String deviceName;
        @Nullable
        private String deviceNameContains;
        @Nullable
        private UUID serviceUuid;
        @Nullable
        private UUID serviceUuidMask;
        @Nullable
        private UUID solicitationUuid;
        @Nullable
        private UUID solicitationUuidMask;
        @Nullable
        private UUID serviceDataUuid;
        @Nullable
        private byte[] serviceData;
        @Nullable
        private byte[] serviceDataMask;
        @Nullable
        private Integer manufacturerId;
        @Nullable
        private byte[] manufacturerData;
        @Nullable
        private byte[] manufacturerDataMask;
        @Nullable
        private Integer minRssi;
        private boolean requireConnectable;

        @NonNull
        public Builder deviceAddress(@Nullable String deviceAddress) {
            if (deviceAddress != null && !BleDeviceId.isValid(deviceAddress)) {
                throw new IllegalArgumentException("Invalid MAC address: " + deviceAddress);
            }
            this.deviceAddress = deviceAddress;
            return this;
        }

        @NonNull
        public Builder deviceName(@Nullable String deviceName) {
            this.deviceName = deviceName;
            return this;
        }

        @NonNull
        public Builder deviceNameContains(@Nullable String deviceNameContains) {
            this.deviceNameContains = deviceNameContains;
            return this;
        }

        @NonNull
        public Builder serviceUuid(@NonNull UUID serviceUuid) {
            return serviceUuid(serviceUuid, null);
        }

        @NonNull
        public Builder serviceUuid(@NonNull UUID serviceUuid, @Nullable UUID serviceUuidMask) {
            this.serviceUuid = Objects.requireNonNull(serviceUuid, "serviceUuid");
            this.serviceUuidMask = serviceUuidMask;
            return this;
        }

        @NonNull
        public Builder solicitationUuid(@NonNull UUID solicitationUuid) {
            return solicitationUuid(solicitationUuid, null);
        }

        @NonNull
        public Builder solicitationUuid(@NonNull UUID solicitationUuid,
                                        @Nullable UUID solicitationUuidMask) {
            this.solicitationUuid = Objects.requireNonNull(solicitationUuid, "solicitationUuid");
            this.solicitationUuidMask = solicitationUuidMask;
            return this;
        }

        @NonNull
        public Builder serviceData(@NonNull UUID serviceDataUuid, @NonNull byte[] serviceData) {
            return serviceData(serviceDataUuid, serviceData, null);
        }

        @NonNull
        public Builder serviceData(@NonNull UUID serviceDataUuid,
                                   @NonNull byte[] serviceData,
                                   @Nullable byte[] serviceDataMask) {
            Objects.requireNonNull(serviceDataUuid, "serviceDataUuid");
            Objects.requireNonNull(serviceData, "serviceData");
            if (serviceDataMask != null && serviceDataMask.length != serviceData.length) {
                throw new IllegalArgumentException("serviceDataMask length mismatch");
            }
            this.serviceDataUuid = serviceDataUuid;
            this.serviceData = serviceData.clone();
            this.serviceDataMask = serviceDataMask != null ? serviceDataMask.clone() : null;
            return this;
        }

        @NonNull
        public Builder manufacturerData(int manufacturerId, @NonNull byte[] data) {
            return manufacturerData(manufacturerId, data, null);
        }

        @NonNull
        public Builder manufacturerData(int manufacturerId,
                                        @NonNull byte[] data,
                                        @Nullable byte[] mask) {
            if (manufacturerId < 0 || manufacturerId > 0xFFFF) {
                throw new IllegalArgumentException("Invalid manufacturerId: " + manufacturerId);
            }
            Objects.requireNonNull(data, "manufacturerData");
            if (mask != null && mask.length != data.length) {
                throw new IllegalArgumentException("manufacturerDataMask length mismatch");
            }
            this.manufacturerId = manufacturerId;
            this.manufacturerData = data.clone();
            this.manufacturerDataMask = mask != null ? mask.clone() : null;
            return this;
        }

        @NonNull
        public Builder minRssi(int minRssi) {
            this.minRssi = minRssi;
            return this;
        }

        @NonNull
        public Builder requireConnectable(boolean requireConnectable) {
            this.requireConnectable = requireConnectable;
            return this;
        }

        @NonNull
        public BleScanFilter build() {
            return new BleScanFilter(this);
        }
    }

    @Nullable
    private static byte[] copyOf(@Nullable byte[] bytes) {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }
}
