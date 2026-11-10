package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;

/** 扫描页标题栏筛选下拉项：modelId 为空串表示展示全部已识别设备类型。 */
public record ScanFilterOption(@NonNull String modelId, @NonNull String label) {

    public static final String ALL_MODELS = "";

    public ScanFilterOption {
        modelId = modelId == null ? ALL_MODELS : modelId;
        label = label == null ? "" : label;
    }

    public boolean isAll() {
        return modelId.isEmpty();
    }
}
