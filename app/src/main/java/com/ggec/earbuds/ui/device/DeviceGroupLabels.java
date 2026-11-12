package com.ggec.earbuds.ui.device;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;

/** 能力分组的界面词汇。分组来自公共设备协议，不是某个项目的 API。 */
public final class DeviceGroupLabels {

    private DeviceGroupLabels() {
    }

    @StringRes
    public static int title(@NonNull DeviceCapabilityGroup group) {
        return switch (group) {
            case MUSIC -> R.string.device_entry_music;
            case CONTROL -> R.string.device_entry_control;
            case FUNCTION -> R.string.device_entry_function;
            case DUAL_CONNECTION -> R.string.device_entry_dual;
            case RECORD -> R.string.device_entry_record;
            case ABOUT -> R.string.device_entry_about;
        };
    }

    @DrawableRes
    public static int icon(@NonNull DeviceCapabilityGroup group) {
        return switch (group) {
            case MUSIC -> R.drawable.ic_ai_bolt;
            case CONTROL -> R.drawable.ic_ai_settings;
            case FUNCTION -> R.drawable.ic_ai_shield;
            case DUAL_CONNECTION -> R.drawable.ic_ai_swap;
            case RECORD -> R.drawable.ic_ai_call_mic;
            case ABOUT -> R.drawable.ic_ai_globe;
        };
    }
}
