package com.ggec.earbuds.ui.device.settings;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.ui.device.DeviceGroupLabels;

import java.util.ArrayList;
import java.util.List;

/** 领域快照 + 能力描述 → 设置页界面状态。不碰 View，也不判断机型。 */
public final class DeviceSettingsUiMapper {

    private final SettingsSectionFactory sections;

    public DeviceSettingsUiMapper(@NonNull SettingsSectionFactory sections) {
        this.sections = sections;
    }

    public DeviceSettingsUiState map(@NonNull DeviceCapabilityGroup group,
                                     DeviceProfileView profile, DeviceSnapshot snapshot) {
        ConnectionState connection = snapshot.connection();
        if (profile == null || snapshot == null
                || (connection != ConnectionState.READY && connection != ConnectionState.CONNECTING)) {
            return DeviceSettingsUiState.unconnected(group, DeviceGroupLabels.title(group));
        }
        List<DeviceSettingRow> rows = new ArrayList<>();
        if (group == DeviceCapabilityGroup.ABOUT) {
            rows.addAll(AboutInfoRows.build(snapshot));
        }
        rows.addAll(sections.rows(profile, snapshot.state(), group));
        if (group == DeviceCapabilityGroup.ABOUT) {
            rows.add(sections.deviceManagementRow());
        }
        return new DeviceSettingsUiState(
                group,
                DeviceGroupLabels.title(group),
                true,
                snapshot.connection() == ConnectionState.READY,
                rows,
                snapshot.dualConnection(),
                snapshot.recordFiles());
    }
}
