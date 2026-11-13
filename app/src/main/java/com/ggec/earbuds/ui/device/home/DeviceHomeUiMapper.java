package com.ggec.earbuds.ui.device.home;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceEntry;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceGroupLabels;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 台账 + 能力入口 → 设备页。纯映射：不碰 View、Context、存储、时钟。
 *
 * <p>规则：只有「已连接」的设备才给标题、副标题与能力入口；历史设备只出现在下拉里。
 * 型号名与能力都来自 biz 目录，APP 不维护型号表，也不按能力名推断分区。</p>
 */
public final class DeviceHomeUiMapper {

    private final DeviceCapabilityCatalog catalog;

    public DeviceHomeUiMapper(@NonNull DeviceCapabilityCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @NonNull
    public DeviceHomeUiState map(@NonNull DeviceRoster roster,
                                 @NonNull List<DeviceEntry> entries) {
        DeviceRecord active = liveActive(roster);
        List<DeviceHomeUiState.Choice> choices = new ArrayList<>(roster.devices().size());
        for (DeviceRecord record : roster.sorted()) {
            choices.add(choice(record, roster.activeId()));
        }
        List<DeviceHomeUiState.Entry> cards = new ArrayList<>(entries.size());
        for (DeviceEntry entry : entries) {
            cards.add(new DeviceHomeUiState.Entry(entry.group(),
                    DeviceGroupLabels.title(entry.group()),
                    DeviceGroupLabels.icon(entry.group()),
                    entry.capabilityCount()));
        }
        if (active == null) {
            boolean known = !choices.isEmpty();
            // 没有连接中的设备就没有能力入口：入口描述的是“这台设备现在能做什么”。
            return new DeviceHomeUiState("", "", choices, List.of(),
                    R.string.device_home_empty_title,
                    known ? R.string.device_home_disconnected_body
                            : R.string.device_home_empty_body);
        }
        return new DeviceHomeUiState(nameOf(active), subtitle(active), choices, cards,
                R.string.device_home_empty_title, R.string.device_home_empty_body);
    }

    private static DeviceRecord liveActive(@NonNull DeviceRoster roster) {
        Optional<DeviceId> activeId = roster.activeId();
        if (activeId.isEmpty()) {
            return null;
        }
        DeviceRecord record = roster.find(activeId.get()).orElse(null);
        return record != null && record.live() ? record : null;
    }

    @NonNull
    private static String nameOf(@NonNull DeviceRecord record) {
        String classic = record.identity().classicName();
        if (!classic.isEmpty()) {
            return classic;
        }
        String ble = record.identity().bleName();
        return ble.isEmpty() ? record.displayName() : ble;
    }

    @NonNull
    private String subtitle(@NonNull DeviceRecord record) {
        return record.id().displayAddress() + DeviceFormats.META_SEPARATOR
                + modelName(record.id().modelId());
    }

    @NonNull
    private DeviceHomeUiState.Choice choice(@NonNull DeviceRecord record,
                                            @NonNull Optional<DeviceId> activeId) {
        boolean selected = activeId.map(id -> id.equals(record.id())).orElse(false);
        return new DeviceHomeUiState.Choice(
                record.id().key(),
                nameOf(record),
                record.id().displayAddress(),
                modelName(record.id().modelId()),
                selected && record.live());
    }

    /** 型号显示名来自 biz 设备资料；查不到就显示型号 id，不编名字。 */
    @NonNull
    private String modelName(@NonNull String modelId) {
        for (DeviceProfileView profile : catalog.models()) {
            if (profile.modelId().equals(modelId)) {
                return profile.displayName();
            }
        }
        return modelId;
    }
}
