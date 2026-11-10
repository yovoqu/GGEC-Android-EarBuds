package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * SDK 归一化后的扫描结果。APP 只拿到 modelId + variantId，不接触广播字节、
 * FeatureCode、market 或扫描器类型。
 */
public record ScanResult(
        String modelId,
        String variantId,
        String name,
        String mac,
        int rssi,
        boolean paired
) {

    public ScanResult {
        Objects.requireNonNull(modelId, "modelId");
        variantId = variantId == null ? "" : variantId;
        name = name == null ? "" : name;
        mac = mac == null ? "" : mac;
    }

    public boolean sameDevice(ScanResult other) {
        return other != null
                && mac.equalsIgnoreCase(other.mac)
                && modelId.equals(other.modelId);
    }
}
