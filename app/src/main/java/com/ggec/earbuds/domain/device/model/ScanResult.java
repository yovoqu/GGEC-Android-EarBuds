package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 扫描页使用的 BLE 观察结果。只描述正在广播的设备：广播名和 BLE 地址。
 * 不包含是否已配对。已配对列表属于经典蓝牙的单独查询。
 *
 * <p><b>契约：</b>四个字符串字段均非空。广播里没有某个字段时由产生数据的
 * {@code biz_ggec_hs} 写成空串（「广播未带该值」是协议事实），本记录不把 null
 * 静默改写成空串——传 null 说明上游漏赋值，在构造点直接抛出。</p>
 */
public record ScanResult(
        String modelId,
        String variantId,
        String bleName,
        String bleMac,
        int rssi
) {

    public ScanResult {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(bleName, "bleName");
        Objects.requireNonNull(bleMac, "bleMac");
    }

    /** {@code other} 非空：比较两个候选设备，null 不是「不相等」，而是调用方漏传。 */
    public boolean sameDevice(ScanResult other) {
        return bleMac.equalsIgnoreCase(Objects.requireNonNull(other, "other").bleMac)
                && modelId.equals(other.modelId);
    }
}
