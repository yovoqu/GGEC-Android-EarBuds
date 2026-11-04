package com.ggec.earbuds.ui.device.settings;

import java.util.List;

/** 设置页的行：强类型封闭集合，替代旧的 Row + kind 通用模型。 */
public sealed interface DeviceSettingRow {

    record Section(int titleRes) implements DeviceSettingRow {
    }

    record Info(int titleRes, String value) implements DeviceSettingRow {
    }

    record Choice(SettingId id, int titleRes, int valueRes, List<Integer> optionRes)
            implements DeviceSettingRow {

        public Choice {
            optionRes = optionRes == null ? List.of() : List.copyOf(optionRes);
        }
    }

    record Toggle(SettingId id, int titleRes, boolean checked) implements DeviceSettingRow {
    }

    record Action(SettingId id, int titleRes, boolean destructive) implements DeviceSettingRow {
    }
}
