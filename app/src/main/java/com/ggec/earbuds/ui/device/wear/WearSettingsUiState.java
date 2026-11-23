package com.ggec.earbuds.ui.device.wear;

/** 佩戴设置页界面状态：佩戴检测开关的设备事实 + 可写状态。 */
public record WearSettingsUiState(boolean connected, boolean checked, boolean enabled) {

    public static WearSettingsUiState idle() {
        return new WearSettingsUiState(false, false, false);
    }
}
