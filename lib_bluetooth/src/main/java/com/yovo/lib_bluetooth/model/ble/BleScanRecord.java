package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BLE AD 广播载荷结构化解析结果（完全不依赖 Android Framework 类，可直接在 JVM 中构造与断言）。
 */
public final class BleScanRecord {

    private final int advertiseFlags;
    @NonNull
    private final List<UUID> serviceUuids;
    @NonNull
    private final List<UUID> serviceSolicitationUuids;
    @NonNull
    private final Map<UUID, byte[]> serviceData;
    @NonNull
    private final Map<Integer, byte[]> manufacturerSpecificData;
    private final int txPowerLevel;
    @Nullable
    private final String deviceName;
    @NonNull
    private final byte[] rawBytes;

    public BleScanRecord(int advertiseFlags,
                         @NonNull List<UUID> serviceUuids,
                         @NonNull List<UUID> serviceSolicitationUuids,
                         @NonNull Map<UUID, byte[]> serviceData,
                         @NonNull Map<Integer, byte[]> manufacturerSpecificData,
                         int txPowerLevel,
                         @Nullable String deviceName,
                         @NonNull byte[] rawBytes) {
        this.advertiseFlags = advertiseFlags;
        this.serviceUuids = Collections.unmodifiableList(new ArrayList<>(serviceUuids));
        this.serviceSolicitationUuids = Collections.unmodifiableList(
                new ArrayList<>(serviceSolicitationUuids));

        Map<UUID, byte[]> copiedServiceData = new LinkedHashMap<>();
        for (Map.Entry<UUID, byte[]> entry : serviceData.entrySet()) {
            copiedServiceData.put(entry.getKey(),
                    entry.getValue() == null ? new byte[0] : entry.getValue().clone());
        }
        this.serviceData = Collections.unmodifiableMap(copiedServiceData);

        Map<Integer, byte[]> copiedManu = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> entry : manufacturerSpecificData.entrySet()) {
            copiedManu.put(entry.getKey(),
                    entry.getValue() == null ? new byte[0] : entry.getValue().clone());
        }
        this.manufacturerSpecificData = Collections.unmodifiableMap(copiedManu);

        this.txPowerLevel = txPowerLevel;
        this.deviceName = deviceName;
        this.rawBytes = rawBytes.clone();
    }

    @NonNull
    public static BleScanRecord empty() {
        return new BleScanRecord(
                -1,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Integer.MIN_VALUE,
                null,
                new byte[0]);
    }

    public int advertiseFlags() {
        return advertiseFlags;
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
        for (Map.Entry<UUID, byte[]> e : serviceData.entrySet()) {
            copy.put(e.getKey(), e.getValue().clone());
        }
        return copy;
    }

    @Nullable
    public byte[] getServiceData(@NonNull UUID serviceDataUuid) {
        byte[] value = serviceData.get(serviceDataUuid);
        return value == null ? null : value.clone();
    }

    @NonNull
    public Map<Integer, byte[]> manufacturerSpecificData() {
        Map<Integer, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> e : manufacturerSpecificData.entrySet()) {
            copy.put(e.getKey(), e.getValue().clone());
        }
        return copy;
    }

    @Nullable
    public byte[] getManufacturerSpecificData(int manufacturerId) {
        byte[] value = manufacturerSpecificData.get(manufacturerId);
        return value == null ? null : value.clone();
    }

    /** 发射功率电平（dBm），未包含在广告包中时为 {@link Integer#MIN_VALUE}。 */
    public int txPowerLevel() {
        return txPowerLevel;
    }

    @Nullable
    public String deviceName() {
        return deviceName;
    }

    @NonNull
    public byte[] rawBytes() {
        return Arrays.copyOf(rawBytes, rawBytes.length);
    }
}
