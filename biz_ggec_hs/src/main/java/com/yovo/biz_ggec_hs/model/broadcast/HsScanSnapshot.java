package com.yovo.biz_ggec_hs.model.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 一次扫描观察到的原始广播。这是广播层的输入，不包含 GATT，也不引用蓝牙库类型。
 */
public final class HsScanSnapshot {
    @Nullable
    private final String bleAddress;
    @NonNull
    private final List<UUID> serviceUuids;
    @NonNull
    private final byte[] rawAdvertising;
    @Nullable
    private final String deviceName;

    private HsScanSnapshot(@Nullable String bleAddress,
                           @NonNull List<UUID> serviceUuids,
                           @NonNull byte[] rawAdvertising,
                           @Nullable String deviceName) {
        this.bleAddress = bleAddress;
        this.serviceUuids = Collections.unmodifiableList(new ArrayList<>(serviceUuids));
        this.rawAdvertising = rawAdvertising.clone();
        this.deviceName = deviceName;
    }

    @NonNull
    public static HsScanSnapshot of(@Nullable String bleAddress,
                                    @NonNull List<UUID> serviceUuids,
                                    @NonNull byte[] rawAdvertising,
                                    @Nullable String deviceName) {
        return new HsScanSnapshot(
                bleAddress,
                serviceUuids,
                rawAdvertising,
                deviceName);
    }

    @Nullable
    public String bleAddress() {
        return bleAddress;
    }

    @NonNull
    public List<UUID> serviceUuids() {
        return serviceUuids;
    }

    /**
     * 原始广播字节的唯一取值入口：返回构造时已克隆的内部数组，本方法不再复制。
     * 调用方只读，不得就地写入；广播解码等逐包必走的热路径用它避免逐包 clone。
     */
    @NonNull
    public byte[] rawAdvertisingView() {
        return rawAdvertising;
    }

    @Nullable
    public String deviceName() {
        return deviceName;
    }
}
