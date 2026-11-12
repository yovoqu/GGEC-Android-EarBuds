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
 * BLE AD 广播载荷不可变结构化解析结果（独立于 Android Framework 类型）。
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
        return EMPTY;
    }

    /** 共享的“空广播记录”单例，避免每包解析空/无 AD 载荷时重复分配。 */
    private static final BleScanRecord EMPTY = new BleScanRecord(
            -1,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Integer.MIN_VALUE,
            null,
            new byte[0]);

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

    // ---------------------------------------------------------------------------------------------
    // 只读视图（每包热路径）：直接返回底层数组而不逐次 clone，用于扫描过滤匹配、广播协议解码等
    // “每帧必走且只读”的场景，避免逐包副本造成的分配风暴。
    //
    // 契约：返回数组由本对象持有，调用方只读，禁止写入/排序/截断；需要可变副本时改用
    // rawBytes() / getServiceData() / getManufacturerSpecificData()。
    // ---------------------------------------------------------------------------------------------

    /**
     * 只读视图：返回底层原始广播字节数组。
     *
     * <p><b>调用方禁止修改返回数组</b>（写入会污染所有持有同一记录的调用方）；需要可变副本时用
     * {@link #rawBytes()}。仅用于库内与协议层的逐帧只读路径，不是通用取值入口。</p>
     */
    @NonNull
    public byte[] rawBytesView() {
        return rawBytes;
    }

    /**
     * 只读视图：返回指定 serviceData 的底层数组（无则 {@code null}）。
     *
     * <p><b>调用方禁止修改返回数组</b>；需要可变副本时用 {@link #getServiceData(UUID)}。
     * 仅用于扫描过滤等库内热路径。</p>
     */
    @Nullable
    public byte[] getServiceDataView(@NonNull UUID serviceDataUuid) {
        return serviceData.get(serviceDataUuid);
    }

    /**
     * 只读视图：返回指定 manufacturerId 的底层数组（无则 {@code null}）。
     *
     * <p><b>调用方禁止修改返回数组</b>；需要可变副本时用 {@link #getManufacturerSpecificData(int)}。
     * 仅用于扫描过滤等库内热路径。</p>
     */
    @Nullable
    public byte[] getManufacturerSpecificDataView(int manufacturerId) {
        return manufacturerSpecificData.get(manufacturerId);
    }
}
