package com.ggec.earbuds.ui.device.settings.page;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.SettingId;

import java.util.ArrayList;
import java.util.List;

/** 关于设备页的强类型行。 */
public final class AboutPageRows {

    private AboutPageRows() {
    }

    public static List<DeviceSettingRow> build(DeviceProfile profile, DeviceSnapshot snapshot) {
        List<DeviceSettingRow> rows = new ArrayList<>();
        rows.add(new DeviceSettingRow.Section(R.string.device_section_info));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_model, profile.model().id()));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_firmware,
                DeviceFormats.textOrUnknown(snapshot.firmware())));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_mac,
                DeviceFormats.textOrUnknown(snapshot.mac())));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_color,
                DeviceFormats.textOrUnknown(snapshot.colorLabel())));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_rename,
                DeviceFormats.textOrUnknown(snapshot.deviceName())));

        if (profile.supports(Capability.CLEAR_PAIRING)) {
            rows.add(new DeviceSettingRow.Section(R.string.device_section_actions));
            rows.add(new DeviceSettingRow.Action(SettingId.CLEAR_PAIRING,
                    R.string.device_row_clear_pairing, true));
        }

        rows.add(new DeviceSettingRow.Section(R.string.device_section_caps));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_cap_count,
                String.valueOf(profile.capabilities().size())));
        return List.copyOf(rows);
    }
}
