package com.ggec.earbuds.ui.device.home;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceGroupLabels;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 协议快照 + 能力描述 → 设备首页。不调用项目 SDK。 */
public final class DeviceHomeUiMapper {

    public DeviceHomeUiState map(DeviceProfileView profile, DeviceSnapshot snapshot) {
        if (profile == null || snapshot == null) {
            return DeviceHomeUiState.disconnected();
        }
        ConnectionState connection = snapshot.connection();
        if (connection != ConnectionState.READY && connection != ConnectionState.CONNECTING) {
            return DeviceHomeUiState.disconnected();
        }
        BatterySnapshot battery = snapshot.battery();
        String bleName = snapshot.bleName();
        return new DeviceHomeUiState(
                DeviceFormats.textOrUnknown(bleName.isEmpty() ? snapshot.modelId() : bleName),
                bleName.isEmpty() ? "" : snapshot.modelId(),
                connectionTextRes(connection),
                true,
                DeviceFormats.battery(battery.left()),
                DeviceFormats.battery(battery.right()),
                DeviceFormats.battery(battery.caseLevel()),
                wearTextRes(snapshot.wearState()),
                battery.caseCharging() ? R.string.device_charging_on : 0,
                snapshot.firmware(),
                connection == ConnectionState.READY,
                sections(profile, snapshot.state()));
    }

    static List<DeviceHomeUiState.Section> sections(DeviceProfileView profile, DeviceState state) {
        Map<DeviceCapabilityGroup, List<DeviceHomeUiState.Row>> grouped =
                new EnumMap<>(DeviceCapabilityGroup.class);
        for (DeviceCapabilityView capability : profile.capabilities()) {
            row(capability, state).ifPresent(row ->
                    grouped.computeIfAbsent(capability.group(), group -> new ArrayList<>()).add(row));
        }
        List<DeviceHomeUiState.Section> sections = new ArrayList<>();
        for (DeviceCapabilityGroup group : DeviceCapabilityGroup.values()) {
            List<DeviceHomeUiState.Row> rows = grouped.get(group);
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            sections.add(new DeviceHomeUiState.Section(group, DeviceGroupLabels.title(group), rows));
        }
        return sections;
    }

    private static Optional<DeviceHomeUiState.Row> row(DeviceCapabilityView capability, DeviceState state) {
        return switch (capability.kind()) {
            case TOGGLE -> Optional.of(new DeviceHomeUiState.Row.Toggle(
                    capability.id(), capability.title(), toggleOf(state, capability.id())));
            case CHOICE -> Optional.of(new DeviceHomeUiState.Row.Choice(
                    capability.id(), capability.title(), choiceLabel(capability, state),
                    capability.options()));
            case GATE -> Optional.empty();
            case ACTION, LEVEL, CURVE, LIST -> Optional.of(new DeviceHomeUiState.Row.Link(
                    capability.id(), capability.title()));
        };
    }

    private static Optional<Boolean> toggleOf(DeviceState state, String capabilityId) {
        return state.value(capabilityId).flatMap(DeviceHomeUiMapper::asToggle);
    }

    private static Optional<Boolean> asToggle(ControlValue value) {
        return switch (value) {
            case ControlValue.Toggle toggle -> Optional.of(toggle.enabled());
            case ControlValue.Level level -> Optional.empty();
            case ControlValue.Choice choice -> Optional.empty();
            case ControlValue.Curve curve -> Optional.empty();
        };
    }

    private static Optional<String> choiceLabel(DeviceCapabilityView capability, DeviceState state) {
        return state.value(capability.id()).flatMap(value -> switch (value) {
            case ControlValue.Choice choice -> optionTitle(capability.options(), choice.optionId());
            case ControlValue.Toggle toggle -> Optional.empty();
            case ControlValue.Level level -> Optional.empty();
            case ControlValue.Curve curve -> Optional.empty();
        });
    }

    private static Optional<String> optionTitle(List<CapabilityOption> options, String optionId) {
        for (CapabilityOption option : options) {
            if (option.id().equals(optionId)) {
                return Optional.of(option.title());
            }
        }
        return Optional.empty();
    }

    private static int connectionTextRes(ConnectionState state) {
        return switch (state) {
            case IDLE -> R.string.device_state_idle;
            case CONNECTING -> R.string.device_state_connecting;
            case READY -> R.string.device_state_ready;
            case DISCONNECTED -> R.string.device_state_disconnected;
        };
    }

    private static int wearTextRes(WearState wear) {
        return switch (wear) {
            case UNKNOWN -> R.string.device_wear_unknown;
            case NOT_WORN -> R.string.device_wear_none;
        };
    }
}
