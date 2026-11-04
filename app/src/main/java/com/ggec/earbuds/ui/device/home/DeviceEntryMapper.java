package com.ggec.earbuds.ui.device.home;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.ui.device.DevicePage;

import java.util.ArrayList;
import java.util.List;

/** 按能力清单生成设备首页的功能入口。 */
public final class DeviceEntryMapper {

    private DeviceEntryMapper() {
    }

    public static List<DeviceHomeUiState.Entry> map(DeviceProfile profile) {
        List<DeviceHomeUiState.Entry> entries = new ArrayList<>();
        if (profile.supports(Capability.SOUND_QUALITY) || profile.supports(Capability.EQ_MODE)
                || profile.supports(Capability.CUSTOM_EQ)) {
            entries.add(new DeviceHomeUiState.Entry(DevicePage.MUSIC, R.string.device_entry_music,
                    R.drawable.ic_ai_bolt));
        }
        if (profile.supports(Capability.TOUCH_LONG) || profile.supports(Capability.TOUCH_SHORT)
                || profile.supports(Capability.TOUCH_DOUBLE) || profile.supports(Capability.TOUCH_TAP)) {
            entries.add(new DeviceHomeUiState.Entry(DevicePage.CONTROL, R.string.device_entry_control,
                    R.drawable.ic_ai_settings));
        }
        if (profile.supports(Capability.WEAR_DETECT) || profile.supports(Capability.VOICE_WAKEUP)
                || profile.supports(Capability.GAME_MODE) || profile.supports(Capability.FALL_ALERT)
                || profile.supports(Capability.FIND_EAR) || profile.supports(Capability.VOLUME_REMIND)
                || profile.supports(Capability.BEGINNER_MODE) || profile.supports(Capability.INTERPRET)) {
            entries.add(new DeviceHomeUiState.Entry(DevicePage.FUNCTION, R.string.device_entry_function,
                    R.drawable.ic_ai_shield));
        }
        if (profile.supports(Capability.DUAL_CONNECTION)) {
            entries.add(new DeviceHomeUiState.Entry(DevicePage.DUAL, R.string.device_entry_dual,
                    R.drawable.ic_ai_swap));
        }
        if (profile.supports(Capability.RECORD_LOCAL) || profile.supports(Capability.RECORD_STREAM)
                || profile.supports(Capability.RECORD_LIST) || profile.supports(Capability.RECORD_TRANSFER)) {
            entries.add(new DeviceHomeUiState.Entry(DevicePage.RECORD, R.string.device_entry_record,
                    R.drawable.ic_ai_call_mic));
        }
        if (profile.supports(Capability.DEVICE_INFO)) {
            entries.add(new DeviceHomeUiState.Entry(DevicePage.ABOUT, R.string.device_entry_about,
                    R.drawable.ic_ai_globe));
        }
        return List.copyOf(entries);
    }
}
