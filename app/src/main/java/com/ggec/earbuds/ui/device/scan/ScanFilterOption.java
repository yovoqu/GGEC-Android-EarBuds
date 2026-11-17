package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 扫描页标题栏筛选下拉项：单一按照具体产品型号来（如 HS02, HS01, WY01），严禁捆绑或保留"全部设备"逻辑。
 */
public record ScanFilterOption(@NonNull String modelId, @NonNull String label) {

    public ScanFilterOption {
        modelId = Objects.requireNonNull(modelId, "modelId cannot be null").trim();
        label = (label == null || label.trim().isEmpty()) ? modelId : label.trim();
    }

    public boolean isAll() {
        return modelId.isEmpty();
    }
}
