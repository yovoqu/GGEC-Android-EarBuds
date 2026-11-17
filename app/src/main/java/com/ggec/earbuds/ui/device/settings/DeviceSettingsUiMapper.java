package com.ggec.earbuds.ui.device.settings;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.ui.device.DeviceGroupLabels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 领域快照 + 能力描述 → 设置页界面状态。不碰 View，也不判断机型。
 *
 * <p>不变量：连接状态为 {@code READY} 时快照必有 {@code modelId}（由端口实现保证），
 * 调用方据此查目录；查不到描述是「目录缺这款机型」的配置事实，落成显式空态
 * （{@link DeviceSettingsUiState#descriptorMissing}），不静默渲染成空页、也不假装未连接。</p>
 */
public final class DeviceSettingsUiMapper {

    private final SettingsSectionFactory sections;

    public DeviceSettingsUiMapper(@NonNull SettingsSectionFactory sections) {
        this.sections = Objects.requireNonNull(sections, "sections");
    }

    @NonNull
    public DeviceSettingsUiState map(@NonNull DeviceCapabilityGroup group,
                                     @NonNull Optional<DeviceProfileView> profile,
                                     @NonNull DeviceSnapshot snapshot,
                                     @NonNull Map<String, DeviceSettingsUiState.WriteSlot> writes) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(writes, "writes");

        ConnectionState connection = snapshot.connection();
        if (connection != ConnectionState.READY && connection != ConnectionState.CONNECTING) {
            return DeviceSettingsUiState.unconnected(group, DeviceGroupLabels.title(group));
        }
        boolean interactive = connection == ConnectionState.READY;
        if (profile.isEmpty()) {
            return DeviceSettingsUiState.descriptorMissing(group, DeviceGroupLabels.title(group),
                    interactive, snapshot.dualConnection(), snapshot.recordFiles());
        }
        DeviceProfileView description = profile.get();
        List<DeviceSettingRow> rows = new ArrayList<>();
        if (group == DeviceCapabilityGroup.ABOUT) {
            rows.addAll(AboutInfoRows.build(snapshot));
        }
        rows.addAll(sections.rows(description, snapshot.state(), group));
        if (group == DeviceCapabilityGroup.ABOUT) {
            rows.add(sections.deviceManagementRow());
        }
        return new DeviceSettingsUiState(
                group,
                DeviceGroupLabels.title(group),
                true,
                interactive,
                rows,
                writes,
                snapshot.dualConnection(),
                snapshot.recordFiles());
    }
}
