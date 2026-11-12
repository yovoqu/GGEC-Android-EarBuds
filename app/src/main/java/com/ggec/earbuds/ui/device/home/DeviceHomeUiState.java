package com.ggec.earbuds.ui.device.home;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DevicePage;

import java.util.List;

/** 设备页签的界面状态（不可变）。 */
public record DeviceHomeUiState(
        String modelId,
        int connectionTextRes,
        boolean connected,
        String leftText,
        String rightText,
        String caseText,
        int wearTextRes,
        boolean caseCharging,
        int chargingTextRes,
        String firmware,
        boolean controlsAvailable,
        boolean gameMode,
        boolean wearDetect,
        boolean voiceWakeup,
        boolean showGame,
        boolean showWear,
        boolean showVoice,
        List<Entry> entries
) {

    public record Entry(DevicePage page, int titleRes, int iconRes) {
    }

    public static DeviceHomeUiState disconnected() {
        return new DeviceHomeUiState("", R.string.device_state_idle, false,
                DeviceFormats.UNKNOWN, DeviceFormats.UNKNOWN, DeviceFormats.UNKNOWN,
                0, false, 0, "", false, false, false, false, false, false, false, List.of());
    }
}
