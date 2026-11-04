package com.ggec.earbuds.ui.device.settings;

import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.EqPreset;
import com.ggec.earbuds.domain.device.model.SoundQuality;
import com.ggec.earbuds.domain.device.policy.DeviceControlPolicy;
import com.ggec.earbuds.ui.device.DeviceLabels;
import com.ggec.earbuds.ui.device.DevicePage;
import com.ggec.earbuds.ui.device.settings.page.AboutPageRows;
import com.ggec.earbuds.ui.device.settings.page.ControlPageRows;
import com.ggec.earbuds.ui.device.settings.page.DualPageRows;
import com.ggec.earbuds.ui.device.settings.page.FunctionPageRows;
import com.ggec.earbuds.ui.device.settings.page.RecordPageRows;

import java.util.ArrayList;
import java.util.List;

/** 领域快照到设置页强类型界面状态。 */
public final class DeviceSettingsUiMapper {

    public DeviceSettingsUiState map(DevicePage page, DeviceProfile profile, DeviceSnapshot snapshot,
                                     DeviceControls controls) {
        List<DeviceSettingRow> rows = switch (page) {
            case MUSIC -> List.of();
            case CONTROL -> ControlPageRows.build(profile, controls);
            case FUNCTION -> FunctionPageRows.build(profile, controls);
            case DUAL -> DualPageRows.build(profile, controls);
            case RECORD -> RecordPageRows.build(profile);
            case ABOUT -> AboutPageRows.build(profile, snapshot);
        };
        DeviceSettingsUiState.MusicPanel music = page == DevicePage.MUSIC
                ? music(profile, controls) : null;
        return new DeviceSettingsUiState(page.titleRes, rows, music);
    }

    private static DeviceSettingsUiState.MusicPanel music(DeviceProfile profile, DeviceControls controls) {
        List<DeviceSettingsUiState.MusicPanel.Preset> presets = new ArrayList<>();
        List<EqPreset> allowed = DeviceControlPolicy.presets(profile);
        int selected = -1;
        for (int index = 0; index < allowed.size(); index++) {
            EqPreset preset = allowed.get(index);
            presets.add(new DeviceSettingsUiState.MusicPanel.Preset(
                    DeviceLabels.eq(preset), DeviceLabels.eqSubtitle(preset)));
            if (preset == controls.eqPreset()) {
                selected = index;
            }
        }
        List<Integer> qualities = new ArrayList<>();
        List<SoundQuality> qualityValues = DeviceControlPolicy.qualities();
        int selectedQuality = 0;
        for (int index = 0; index < qualityValues.size(); index++) {
            SoundQuality quality = qualityValues.get(index);
            qualities.add(DeviceLabels.quality(quality));
            if (quality == controls.soundQuality()) {
                selectedQuality = index;
            }
        }
        boolean customSelected = controls.eqPreset() == EqPreset.CUSTOM && controls.customEqSaved();
        return new DeviceSettingsUiState.MusicPanel(
                presets,
                customSelected ? -1 : selected,
                controls.customEqSaved(),
                customSelected,
                controls.customEqName(),
                controls.customEqGains(),
                qualities,
                selectedQuality,
                controls.ldac(),
                controls.volumeAdaptive());
    }
}
