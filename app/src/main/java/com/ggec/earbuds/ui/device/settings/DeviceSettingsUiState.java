package com.ggec.earbuds.ui.device.settings;

import java.util.List;

/** 设置页界面状态：行是强类型封闭集合，音乐页附带独立的强类型面板。 */
public record DeviceSettingsUiState(int titleRes, List<DeviceSettingRow> rows, MusicPanel music) {

    public DeviceSettingsUiState {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public record MusicPanel(
            List<Preset> presets,
            int selectedPreset,
            boolean showCustom,
            boolean customSelected,
            String customName,
            float[] customGains,
            List<Integer> qualityLabels,
            int selectedQuality,
            boolean ldac,
            boolean volumeAdaptive) {

        public MusicPanel {
            presets = List.copyOf(presets);
            qualityLabels = List.copyOf(qualityLabels);
            customName = customName == null ? "" : customName;
            float[] gains = new float[10];
            if (customGains != null) {
                System.arraycopy(customGains, 0, gains, 0, Math.min(customGains.length, gains.length));
            }
            customGains = gains;
        }

        public record Preset(int labelRes, int subtitleRes) {
        }
    }
}
