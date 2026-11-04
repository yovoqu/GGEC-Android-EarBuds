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

    public DeviceHomeUiState map(DeviceProfile profile, DeviceSnapshot snapshot, DeviceControls controls) {
        if (profile == null) {
            return DeviceHomeUiState.empty();
        }
        BatterySnapshot battery = snapshot.battery();
        return new DeviceHomeUiState(
                true,
                profile.model().id(),
                connectionTextRes(snapshot.connection()),
                snapshot.isConnected(),
                DeviceFormats.battery(battery.left()),
                DeviceFormats.battery(battery.right()),
                DeviceFormats.battery(battery.caseLevel()),
                wearTextRes(snapshot.wear()),
                battery.caseCharging(),
                battery.caseCharging() ? R.string.device_charging_on : 0,
                snapshot.firmware(),
                controls.enabled(DeviceSwitch.GAME_MODE),
                controls.enabled(DeviceSwitch.WEAR_DETECT),
                controls.enabled(DeviceSwitch.VOICE_WAKEUP),
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

    private static int wearTextRes(WearState wear) {
        return switch (wear) {
            case NOT_WORN -> R.string.device_wear_none;
            case LEFT -> R.string.device_wear_left;
            case RIGHT -> R.string.device_wear_right;
            case BOTH -> R.string.device_wear_both;
        };
    }
}
