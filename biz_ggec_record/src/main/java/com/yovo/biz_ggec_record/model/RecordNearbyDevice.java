package com.yovo.biz_ggec_record.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * 扫描到的附近 Record 设备。不含 Bluetooth 类型。
 */
public final class RecordNearbyDevice {

    @NonNull
    private final String modelId;
    @NonNull
    private final String macAddress;
    @NonNull
    private final String name;
    private final int rssi;

    public RecordNearbyDevice(@NonNull String modelId,
                              @NonNull String macAddress,
                              @Nullable String name,
                              int rssi) {
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.macAddress = Objects.requireNonNull(macAddress, "macAddress")
                .trim()
                .toUpperCase(Locale.ROOT);
        this.name = name == null ? "" : name.trim();
        this.rssi = rssi;
        if (modelId.isEmpty() || this.macAddress.isEmpty()) {
            throw new IllegalArgumentException("RecordNearbyDevice requires modelId and macAddress");
        }
    }

    @NonNull
    public String modelId() {
        return modelId;
    }

    @NonNull
    public String macAddress() {
        return macAddress;
    }

    @NonNull
    public String name() {
        return name;
    }

    public int rssi() {
        return rssi;
    }
}
