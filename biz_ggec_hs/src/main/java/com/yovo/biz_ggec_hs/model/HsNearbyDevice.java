package com.yovo.biz_ggec_hs.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 协议层认出来的一台附近耳机。地址和名称都来自 BLE 扫描，不含经典蓝牙地址。
 */
public record HsNearbyDevice(
        @NonNull String modelId,
        @NonNull String variantId,
        @NonNull String bleName,
        @NonNull String bleMac,
        int rssi
) {
    public HsNearbyDevice {
        modelId = Objects.requireNonNull(modelId, "modelId");
        variantId = Objects.requireNonNull(variantId, "variantId");
        bleName = Objects.requireNonNull(bleName, "bleName");
        bleMac = Objects.requireNonNull(bleMac, "bleMac");
    }
}
