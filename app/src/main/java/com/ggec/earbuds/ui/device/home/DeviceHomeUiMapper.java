package com.ggec.earbuds.ui.device.home;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceSwitch;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.ui.device.DeviceFormats;

/** 领域状态 → 设备首页 UiState。不碰 View。 */
public final class DeviceHomeUiMapper {

    public DeviceHomeUiState map(DeviceProfile profile, DeviceSnapshot snapshot,
                                 DeviceControls controls, boolean controlsAvailable) {
        if (profile == null || snapshot == null) {
            return DeviceHomeUiState.disconnected();
        }
        // 连接中也要给出反馈（否则“点连接→首页仍显示未连接”）；未连接/断开才回落空态。
        boolean showCard = snapshot.isConnected()
                || snapshot.connection() == ConnectionState.CONNECTING;
        if (!showCard) {
            return DeviceHomeUiState.disconnected();
        }
        DeviceControls safeControls = controls == null ? DeviceControls.empty() : controls;
        BatterySnapshot battery = snapshot.battery();
        return new DeviceHomeUiState(
                profile.model().id(),
                connectionTextRes(snapshot.connection()),
                true,
                DeviceFormats.battery(battery.left()),
                DeviceFormats.battery(battery.right()),
                DeviceFormats.battery(battery.caseLevel()),
                wearTextRes(snapshot.wear()),
                battery.caseCharging(),
                battery.caseCharging() ? R.string.device_charging_on : 0,
                snapshot.firmware(),
                controlsAvailable,
                safeControls.enabled(DeviceSwitch.GAME_MODE),
                safeControls.enabled(DeviceSwitch.WEAR_DETECT),
                safeControls.enabled(DeviceSwitch.VOICE_WAKEUP),
                profile.supports(Capability.GAME_MODE),
                profile.supports(Capability.WEAR_DETECT),
                profile.supports(Capability.VOICE_WAKEUP),
                DeviceEntryMapper.map(profile));
    }

    private static int connectionTextRes(ConnectionState state) {
        return switch (state) {
            case IDLE -> R.string.device_state_idle;
            case SCANNING -> R.string.device_state_scanning;
            case CONNECTING -> R.string.device_state_connecting;
            case CONNECTED_NOT_READY -> R.string.device_state_connected_not_ready;
            case READY -> R.string.device_state_ready;
            case DISCONNECTED -> R.string.device_state_disconnected;
        };
    }

    /** 佩戴文案单点：没有数据（{@link WearState#UNKNOWN}）与确知未佩戴给不同文案，不共用一句。 */
    private static int wearTextRes(WearState wear) {
        return switch (wear) {
            case UNKNOWN -> R.string.device_wear_unknown;
            case NOT_WORN -> R.string.device_wear_none;
        };
    }
}
