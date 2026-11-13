package com.ggec.earbuds.domain.device.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 设备页上的一个能力入口：按 biz 目录里的能力分区聚合。
 *
 * <p>只表达「这台设备有哪几组能力、每组几项」，不携带任何界面文案或图标——
 * 标题、图标、顺序都留在 UI 层（{@code DeviceGroupLabels}），领域不认识资源。</p>
 *
 * @param group           能力分区，顺序即 {@link DeviceCapabilityGroup} 的声明顺序
 * @param capabilityCount 该分区下可呈现的能力数量（{@code GATE} 类不计入，它不生成界面）
 */
public record DeviceEntry(@NonNull DeviceCapabilityGroup group, int capabilityCount) {

    public DeviceEntry {
        Objects.requireNonNull(group, "group");
        if (capabilityCount < 0) {
            capabilityCount = 0;
        }
    }
}
