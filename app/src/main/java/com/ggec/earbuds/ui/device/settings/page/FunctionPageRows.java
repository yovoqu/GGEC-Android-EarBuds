package com.ggec.earbuds.ui.device.settings.page;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceSwitch;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.SettingId;

import java.util.ArrayList;
import java.util.List;

/** 功能设置页的强类型行。 */
public final class FunctionPageRows {

    private FunctionPageRows() {
    }

    public static List<DeviceSettingRow> build(DeviceProfile profile, DeviceControls controls) {
        if (profile == null) {
            return List.of();
        }
        DeviceControls safeControls = controls == null ? DeviceControls.empty() : controls;
        List<DeviceSettingRow> rows = new ArrayList<>();
        toggle(rows, profile, safeControls, Capability.WEAR_DETECT, SettingId.WEAR_DETECT,
                R.string.device_toggle_wear);
        toggle(rows, profile, safeControls, Capability.VOICE_WAKEUP, SettingId.VOICE_WAKEUP,
                R.string.device_toggle_voice);
        toggle(rows, profile, safeControls, Capability.GAME_MODE, SettingId.GAME_MODE,
                R.string.device_toggle_game);
        toggle(rows, profile, safeControls, Capability.FALL_ALERT, SettingId.FALL_ALERT,
                R.string.device_row_fall_alert);
        toggle(rows, profile, safeControls, Capability.VOLUME_REMIND, SettingId.VOLUME_REMIND,
                R.string.device_row_volume_remind);
        toggle(rows, profile, safeControls, Capability.BEGINNER_MODE, SettingId.BEGINNER_MODE,
                R.string.device_row_beginner);
        toggle(rows, profile, safeControls, Capability.INTERPRET, SettingId.INTERPRET,
                R.string.device_row_interpret);

        if (profile.supports(Capability.FIND_EAR)) {
            rows.add(new DeviceSettingRow.Section(R.string.device_section_find));
            rows.add(new DeviceSettingRow.Action(SettingId.FIND_LEFT, R.string.device_row_find_left, false));
            rows.add(new DeviceSettingRow.Action(SettingId.FIND_RIGHT, R.string.device_row_find_right, false));
            rows.add(new DeviceSettingRow.Action(SettingId.FIND_STOP, R.string.device_row_find_stop, false));
        }
        return List.copyOf(rows);
    }

    private static void toggle(List<DeviceSettingRow> rows, DeviceProfile profile,
                               DeviceControls controls, Capability capability, SettingId id,
                               int titleRes) {
        if (profile.supports(capability)) {
            rows.add(new DeviceSettingRow.Toggle(id, titleRes, controls.enabled(switchOf(id))));
        }
    }

    private static DeviceSwitch switchOf(SettingId id) {
        return DeviceSwitch.valueOf(id.name());
    }
}
