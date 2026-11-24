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
            case AI -> R.string.device_entry_ai_chat;
            case ABOUT -> R.string.device_entry_about;
        };
    }

    @DrawableRes
    public static int icon(@NonNull DeviceCapabilityGroup group) {
        return switch (group) {
            case MUSIC -> R.drawable.ic_hw_sound;
            case CONTROL -> R.drawable.ic_hw_control;
            case FUNCTION -> R.drawable.ic_hw_settings;
            case DUAL_CONNECTION -> R.drawable.ic_dev_dual;
            case RECORD -> R.drawable.ic_hw_record;
            case AI -> R.drawable.ic_ai_chat;
            case ABOUT -> R.drawable.ic_hw_about;
        };
    }

    /**
     * 首页内联开关的图标。能力 id 只在 {@code DeviceCapabilityIds} 里出现；这里只是给已登记的
     * 三个开关配图标，其余 id 返回 0（不画图标），不猜别的开关长什么样。
     */
    @DrawableRes
    public static int toggleIcon(@NonNull String capabilityId) {
        return switch (capabilityId) {
            case DeviceCapabilityIds.GAME_MODE -> R.drawable.ic_dev_game;
            case DeviceCapabilityIds.WEAR_DETECT -> R.drawable.ic_dev_wear;
            case DeviceCapabilityIds.VOLUME_ADAPTIVE -> R.drawable.ic_dev_volume;
            default -> 0;
        };
    }
}
