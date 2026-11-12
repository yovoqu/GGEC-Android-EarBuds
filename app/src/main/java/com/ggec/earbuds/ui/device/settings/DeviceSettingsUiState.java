package com.ggec.earbuds.ui.device.settings;

import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DualConnectionView;
import com.ggec.earbuds.domain.device.model.RecordFileView;

import java.util.List;
import java.util.Optional;

/**
 * 设置页界面状态（不可变）：一页对应一个 {@link DeviceCapabilityGroup}。
 *
 * <p>{@code rows} 由 {@link SettingsSectionFactory} 按描述符生成；{@code dualConnection} /
 * {@code recordFiles} 是列表类组件的设备事实（来自快照投影，未上报分别为 empty / 空列表）。</p>
 */
public record DeviceSettingsUiState(
        DeviceCapabilityGroup group,
        int titleRes,
        boolean connected,
        boolean interactive,
        List<DeviceSettingRow> rows,
        Optional<DualConnectionView> dualConnection,
        List<RecordFileView> recordFiles
) {

    public DeviceSettingsUiState {
        rows = rows == null ? List.of() : List.copyOf(rows);
        dualConnection = dualConnection == null ? Optional.empty() : dualConnection;
        recordFiles = recordFiles == null ? List.of() : List.copyOf(recordFiles);
    }

    public static DeviceSettingsUiState unconnected(DeviceCapabilityGroup group, int titleRes) {
        return new DeviceSettingsUiState(group, titleRes, false, false, List.of(),
                Optional.empty(), List.of());
    }
}
