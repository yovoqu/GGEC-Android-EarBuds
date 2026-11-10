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
                                    @Nullable List<UUID> serviceUuids,
                                    @Nullable byte[] rawAdvertising,
                                    @Nullable String deviceName) {
        return new HsScanSnapshot(
                bleAddress,
                serviceUuids == null ? Collections.emptyList() : serviceUuids,
                rawAdvertising == null ? new byte[0] : rawAdvertising,
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

    @NonNull
    public byte[] rawAdvertising() {
        return rawAdvertising.clone();
    }

    @Nullable
    public String deviceName() {
        return deviceName;
    }
}
