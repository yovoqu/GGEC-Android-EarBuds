package com.yovo.biz_ggec_record.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 扫描到的附近 Record 设备。不含 Bluetooth 类型。
 */
public final class RecordNearbyDevice {

    private final String macAddress;
    private final String name;
    private final int rssi;

    public RecordNearbyDevice(@NonNull String macAddress, @Nullable String name, int rssi) {
        this.macAddress = Objects.requireNonNull(macAddress, "macAddress");
        this.name = name == null ? "" : name;
        this.rssi = rssi;
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
