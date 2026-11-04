package com.ggec.earbuds.ui.device.settings.page;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.GestureAction;
import com.ggec.earbuds.domain.device.model.GestureSlot;
import com.ggec.earbuds.domain.device.policy.DeviceControlPolicy;
import com.ggec.earbuds.ui.device.DeviceLabels;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.SettingId;

import java.util.ArrayList;
import java.util.List;

/** 控制设置页的强类型行。 */
public final class ControlPageRows {

    private ControlPageRows() {
    }

    public static List<DeviceSettingRow> build(DeviceProfile profile, DeviceControls controls) {
        boolean split = profile.params().tapSplitsEar();
        List<DeviceSettingRow> rows = new ArrayList<>();
        rows.add(new DeviceSettingRow.Section(R.string.device_section_long));
        pair(rows, controls, split, GestureSlot.LONG, SettingId.LONG, R.string.device_row_long,
                GestureSlot.LONG_LEFT, SettingId.LONG_LEFT, R.string.device_row_long_left,
                GestureSlot.LONG_RIGHT, SettingId.LONG_RIGHT, R.string.device_row_long_right);

        rows.add(new DeviceSettingRow.Section(R.string.device_section_short));
        pair(rows, controls, split, GestureSlot.SHORT, SettingId.SHORT, R.string.device_row_short,
                GestureSlot.SHORT_LEFT, SettingId.SHORT_LEFT, R.string.device_row_short_left,
                GestureSlot.SHORT_RIGHT, SettingId.SHORT_RIGHT, R.string.device_row_short_right);

        if (profile.supports(Capability.TOUCH_DOUBLE)) {
            rows.add(new DeviceSettingRow.Section(R.string.device_section_double));
            rows.add(gesture(controls, GestureSlot.DOUBLE_LEFT, SettingId.DOUBLE_LEFT,
                    R.string.device_row_double_left));
            rows.add(gesture(controls, GestureSlot.DOUBLE_RIGHT, SettingId.DOUBLE_RIGHT,
                    R.string.device_row_double_right));
        }

        rows.add(new DeviceSettingRow.Section(R.string.device_section_tap));
        if (split) {
            rows.add(gesture(controls, GestureSlot.TAP_TWICE_LEFT, SettingId.TAP_TWICE_LEFT,
                    R.string.device_row_tap_twice_left));
            rows.add(gesture(controls, GestureSlot.TAP_TRIPLE_LEFT, SettingId.TAP_TRIPLE_LEFT,
                    R.string.device_row_tap_triple_left));
            rows.add(gesture(controls, GestureSlot.TAP_TWICE_RIGHT, SettingId.TAP_TWICE_RIGHT,
                    R.string.device_row_tap_twice_right));
            rows.add(gesture(controls, GestureSlot.TAP_TRIPLE_RIGHT, SettingId.TAP_TRIPLE_RIGHT,
                    R.string.device_row_tap_triple_right));
        } else {
            rows.add(gesture(controls, GestureSlot.TAP_TWICE, SettingId.TAP_TWICE,
                    R.string.device_row_tap_twice));
            rows.add(gesture(controls, GestureSlot.TAP_TRIPLE, SettingId.TAP_TRIPLE,
                    R.string.device_row_tap_triple));
        }
        rows.add(new DeviceSettingRow.Action(SettingId.RESET_CONTROL, R.string.device_row_reset, false));
        return List.copyOf(rows);
    }

    private static void pair(List<DeviceSettingRow> rows, DeviceControls controls, boolean split,
                             GestureSlot single, SettingId singleId, int singleTitle,
                             GestureSlot left, SettingId leftId, int leftTitle,
                             GestureSlot right, SettingId rightId, int rightTitle) {
        if (split) {
            rows.add(gesture(controls, left, leftId, leftTitle));
            rows.add(gesture(controls, right, rightId, rightTitle));
        } else {
            rows.add(gesture(controls, single, singleId, singleTitle));
        }
    }

    private static DeviceSettingRow gesture(DeviceControls controls, GestureSlot slot,
                                            SettingId id, int titleRes) {
        List<Integer> options = new ArrayList<>();
        for (GestureAction action : DeviceControlPolicy.actions(slot)) {
            options.add(DeviceLabels.gesture(action));
        }
        return new DeviceSettingRow.Choice(id, titleRes,
                DeviceLabels.gesture(controls.gesture(slot)), options);
    }
}
