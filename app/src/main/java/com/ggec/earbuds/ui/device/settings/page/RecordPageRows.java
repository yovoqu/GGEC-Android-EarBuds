package com.ggec.earbuds.ui.device.settings.page;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.SettingId;

import java.util.ArrayList;
import java.util.List;

/** 录音页的强类型行。 */
public final class RecordPageRows {

    private RecordPageRows() {
    }

    public static List<DeviceSettingRow> build(DeviceProfile profile) {
        List<DeviceSettingRow> rows = new ArrayList<>();
        rows.add(new DeviceSettingRow.Section(R.string.device_section_record));
        if (profile.supports(Capability.RECORD_LOCAL)) {
            rows.add(new DeviceSettingRow.Action(SettingId.RECORD_START, R.string.device_row_record_start, false));
            rows.add(new DeviceSettingRow.Action(SettingId.RECORD_STOP, R.string.device_row_record_stop, false));
        }
        rows.add(new DeviceSettingRow.Section(R.string.device_section_files));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_record_count, DeviceFormats.UNKNOWN));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_record_capacity, DeviceFormats.UNKNOWN));
        if (profile.supports(Capability.RECORD_TRANSFER)) {
            rows.add(new DeviceSettingRow.Action(SettingId.RECORD_SYNC, R.string.device_row_record_sync, false));
        }
        return List.copyOf(rows);
    }
}
