package com.ggec.earbuds.ui.device;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlRange;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DualConnectionView;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.WearState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 界面测试用来声明能力描述和快照。数据是测试输入，不是一套模拟设备。 */
public final class DeviceProfileFixtures {

    public static final int UNKNOWN_EAR_COLOR = -1;

    private DeviceProfileFixtures() {
    }

    public static DeviceCapabilityView capability(String id, String title,
                                                  DeviceCapabilityGroup group,
                                                  DeviceCapabilityKind kind,
                                                  CapabilityOption... options) {
        return new DeviceCapabilityView(id, title, group, kind, List.of(options),
                Optional.empty());
    }

    public static DeviceCapabilityView level(String id, String title, DeviceCapabilityGroup group,
                                             Integer min, Integer max, Integer step) {
        Optional<ControlRange> range = min == null
                ? Optional.empty()
                : Optional.of(new ControlRange(min, max, step));
        return new DeviceCapabilityView(id, title, group, DeviceCapabilityKind.LEVEL,
                List.of(), range);
    }

    public static CapabilityOption option(String id, String title) {
        return new CapabilityOption(id, title);
    }

    public static DeviceProfileView profile(String modelId, String displayName,
                                            DeviceCapabilityView... capabilities) {
        return new DeviceProfileView(modelId, displayName, List.of(capabilities));
    }

    public static DeviceState state(Map<String, ControlValue> values) {
        return new DeviceState(values);
    }

    public static DeviceState state(String capabilityId, ControlValue value) {
        return new DeviceState(Map.of(capabilityId, value));
    }

    public static DeviceSnapshot ready(String modelId, DeviceState state) {
        return snapshot(ConnectionState.READY, modelId, WearState.NOT_WORN, state,
                Optional.empty(), List.of());
    }

    public static DeviceSnapshot ready(String modelId, DeviceState state,
                                       Optional<DualConnectionView> dualConnection,
                                       List<RecordFileView> recordFiles) {
        return snapshot(ConnectionState.READY, modelId, WearState.NOT_WORN, state,
                dualConnection, recordFiles);
    }

    public static DeviceSnapshot connecting(String modelId) {
        return new DeviceSnapshot(ConnectionState.CONNECTING, modelId,
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", "AA:BB:CC:00:00:09", "", "", "",
                UNKNOWN_EAR_COLOR, new DeviceState(Map.of()), Optional.empty(), List.of());
    }

    public static DeviceSnapshot connecting(String modelId, DeviceState state, WearState wear) {
        return snapshot(ConnectionState.CONNECTING, modelId, wear, state,
                Optional.empty(), List.of());
    }

    public static DeviceSnapshot idle() {
        return new DeviceSnapshot(ConnectionState.IDLE, "", BatterySnapshot.unknown(),
                WearState.UNKNOWN, "", "", "", "", "", UNKNOWN_EAR_COLOR, new DeviceState(Map.of()),
                Optional.empty(), List.of());
    }

    private static DeviceSnapshot snapshot(ConnectionState connection, String modelId,
                                           WearState wearState, DeviceState state,
                                           Optional<DualConnectionView> dualConnection,
                                           List<RecordFileView> recordFiles) {
        return new DeviceSnapshot(connection, modelId,
                new BatterySnapshot(80, 70, -1, true), wearState, "1.2.0.5",
                "AA:BB:CC:00:00:04", modelId + "-display", "", "", 3, state, dualConnection, recordFiles);
    }
}
