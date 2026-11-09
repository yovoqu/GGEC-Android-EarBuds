package com.ggec.earbuds.ui.device.settings.page;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceSwitch;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.SettingId;

import java.util.ArrayList;
import java.util.List;

/** 双连接页的强类型行。 */
public final class DualPageRows {

    private DualPageRows() {
    }

    public static List<DeviceSettingRow> build(DeviceProfile profile, DeviceControls controls) {
        if (profile == null) {
            return List.of();
        }
        DeviceControls safeControls = controls == null ? DeviceControls.empty() : controls;
        List<DeviceSettingRow> rows = new ArrayList<>();
        if (!profile.supports(Capability.DUAL_CONNECTION)) {
            rows.add(new DeviceSettingRow.Info(R.string.device_row_dual_unsupported, ""));
            return List.copyOf(rows);
        }
        rows.add(new DeviceSettingRow.Toggle(SettingId.DUAL_CONNECTION,
                R.string.device_row_dual_switch, safeControls.enabled(DeviceSwitch.DUAL_CONNECTION)));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_priority, DeviceFormats.UNKNOWN));
        rows.add(new DeviceSettingRow.Section(R.string.device_section_paired));
        rows.add(new DeviceSettingRow.Info(R.string.device_paired_empty, ""));
        return List.copyOf(rows);
    }
}
