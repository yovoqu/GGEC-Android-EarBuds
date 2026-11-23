package com.ggec.earbuds.ui.device.find;

import androidx.annotation.NonNull;

/** 查找设备页界面状态：设备名 + 是否可发响铃。 */
public record FindDeviceUiState(@NonNull String name, boolean interactive) {

    public FindDeviceUiState {
        name = name == null ? "" : name;
    }

    @NonNull
    public static FindDeviceUiState idle() {
        return new FindDeviceUiState("", false);
    }
}
