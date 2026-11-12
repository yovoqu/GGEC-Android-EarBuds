package com.ggec.earbuds.ui.device.settings;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.PairedDeviceView;

/** 已配对设备的展示词汇：设备类型与业务状态各一处映射。 */
public final class PairedDeviceLabels {

    private PairedDeviceLabels() {
    }

    @StringRes
    public static int type(@NonNull PairedDeviceView.PairedDeviceType type) {
        return switch (type) {
            case OTHER -> R.string.device_paired_type_other;
            case PHONE -> R.string.device_paired_type_phone;
            case COMPUTER -> R.string.device_paired_type_computer;
            case PAD -> R.string.device_paired_type_pad;
            case TV -> R.string.device_paired_type_tv;
            case WEARABLE -> R.string.device_paired_type_wearable;
        };
    }

    @StringRes
    public static int business(@NonNull PairedDeviceView.PairedBusiness business) {
        return switch (business) {
            case IDLE -> R.string.device_paired_business_idle;
            case MUSIC -> R.string.device_paired_business_music;
            case CALL -> R.string.device_paired_business_call;
        };
    }
}
