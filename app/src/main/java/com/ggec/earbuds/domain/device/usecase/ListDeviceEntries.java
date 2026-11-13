package com.ggec.earbuds.domain.device.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceEntry;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 列出某台设备的能力入口。
 *
 * <p>数据全部来自 biz 设备目录：机型 → 能力清单 → 按分区聚合。APP 不维护「型号 → 功能」映射，
 * 也不按能力名猜分区或控件类型；{@code GATE} 只做功能门控，不产生入口。</p>
 *
 * <p>没有活动设备、或目录里查不到该机型时返回空列表——页面据此进入空态，不编造入口。</p>
 */
public final class ListDeviceEntries {

    private final DeviceCapabilityCatalog catalog;

    public ListDeviceEntries(@NonNull DeviceCapabilityCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @NonNull
    public List<DeviceEntry> execute(@Nullable DeviceId deviceId) {
        if (deviceId == null) {
            return List.of();
        }
        DeviceProfileView profile = catalog.model(deviceId.modelId()).orElse(null);
        if (profile == null) {
            return List.of();
        }
        List<DeviceEntry> entries = new ArrayList<>();
        for (DeviceCapabilityGroup group : DeviceCapabilityGroup.values()) {
            int count = 0;
            for (DeviceCapabilityView capability : profile.capabilities()) {
                if (capability.group() == group && capability.kind() != DeviceCapabilityKind.GATE) {
                    count++;
                }
            }
            if (count > 0) {
                entries.add(new DeviceEntry(group, count));
            }
        }
        return entries;
    }
}
