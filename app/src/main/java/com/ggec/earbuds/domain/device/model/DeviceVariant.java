package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 变种：由 SDK 上报的不透明标识，APP 不解释其来源（区域版 / 硬件版 / 任何协议字段）。
 * label 仅供调试显示，正式界面不展示。
 */
public record DeviceVariant(String id, String modelId, String label) {

    public DeviceVariant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(modelId, "modelId");
        label = label == null ? "" : label;
    }

    public static DeviceVariant of(String modelId, String id) {
        return new DeviceVariant(id, modelId, "");
    }
}
