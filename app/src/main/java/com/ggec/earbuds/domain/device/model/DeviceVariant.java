package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 变种：由 SDK 上报的不透明标识，APP 不解释其来源（区域版 / 硬件版 / 任何协议字段）。
 * label 仅供调试显示，正式界面不展示。
 *
 * <p><b>契约：</b>三个字段均非空；没有调试标签时由 {@link #of(String, String)} 显式写空串，
 * 本记录不把 null 静默改写成空串。</p>
 */
public record DeviceVariant(String id, String modelId, String label) {

    public DeviceVariant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(label, "label");
    }

    public static DeviceVariant of(String modelId, String id) {
        return new DeviceVariant(id, modelId, "");
    }
}
