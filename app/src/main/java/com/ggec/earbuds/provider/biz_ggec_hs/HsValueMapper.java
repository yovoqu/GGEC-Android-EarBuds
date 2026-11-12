package com.ggec.earbuds.provider.biz_ggec_hs;


import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsControl;
import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.DualConnectionView;
import com.ggec.earbuds.domain.device.model.PairedDeviceView;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code biz_ggec_hs} 契约 ↔ APP 领域词汇的唯一映射点。
 *
 * <p>每条映射都按枚举/密封类型的全部分支穷尽展开，不用 {@code default} 兜底：
 * biz 新增一个取值时这里必须编译失败，而不是静默落进某个「其它」分支。</p>
 */
public final class HsValueMapper {

    private HsValueMapper() {
    }

    /** 能力 id 是 biz 能力标识字符串；非法 id 直接抛出，不静默忽略。 */
    public static HsDevice.Capability capabilityOf(String capabilityId) {
        if (capabilityId == null) {
            throw new IllegalArgumentException("capability id must not be null");
        }
        return HsDevice.Capability.valueOf(capabilityId);
    }

    // ---- 描述符 ----

    public static DeviceCapabilityView toCapabilityView(HsDevice.Feature descriptor) {
        return new DeviceCapabilityView(
                descriptor.capability().name(),
                descriptor.title(),
                toGroup(descriptor.group()),
                toKind(descriptor.kind()),
                toOptions(descriptor.options()),
                toRange(descriptor.range()));
    }

    public static DeviceCapabilityGroup toGroup(HsDevice.Group group) {
        return switch (group) {
            case MUSIC -> DeviceCapabilityGroup.MUSIC;
            case CONTROL -> DeviceCapabilityGroup.CONTROL;
            case FUNCTION -> DeviceCapabilityGroup.FUNCTION;
            case DUAL_CONNECTION -> DeviceCapabilityGroup.DUAL_CONNECTION;
            case RECORD -> DeviceCapabilityGroup.RECORD;
            case ABOUT -> DeviceCapabilityGroup.ABOUT;
        };
    }

    public static DeviceCapabilityKind toKind(HsDevice.Form kind) {
        return switch (kind) {
            case TOGGLE -> DeviceCapabilityKind.TOGGLE;
            case LEVEL -> DeviceCapabilityKind.LEVEL;
            case CHOICE -> DeviceCapabilityKind.CHOICE;
            case CURVE -> DeviceCapabilityKind.CURVE;
            case ACTION -> DeviceCapabilityKind.ACTION;
            case LIST -> DeviceCapabilityKind.LIST;
            case GATE -> DeviceCapabilityKind.GATE;
        };
    }

    public static CapabilityOption toOption(HsDevice.Option option) {
        return new CapabilityOption(option.optionId(), option.title());
    }

    public static List<CapabilityOption> toOptions(List<HsDevice.Option> options) {
        List<CapabilityOption> mapped = new ArrayList<>(options.size());
        for (HsDevice.Option option : options) {
            mapped.add(toOption(option));
        }
        return List.copyOf(mapped);
    }

    public static Optional<ControlRange> toRange(Optional<HsDevice.Range> range) {
        return range.map(HsValueMapper::toRange);
    }

    public static ControlRange toRange(HsDevice.Range range) {
        return new ControlRange(range.min(), range.max(), range.step());
    }

    // ---- 控制值 ----

    /** 状态上报方向：biz 值 → APP 呈现值。 */
    public static ControlValue toControlValue(HsControl.Value value) {
        return switch (value) {
            case HsControl.Value.Toggle toggle -> new ControlValue.Toggle(toggle.enabled());
            case HsControl.Value.Level level -> new ControlValue.Level(level.value());
            case HsControl.Value.Choice choice -> new ControlValue.Choice(choice.optionId());
            case HsControl.Value.Curve curve -> new ControlValue.Curve(curve.points());
        };
    }

    /** 写入方向：APP 呈现值 → biz 值。 */
    public static HsControl.Value toHsControlValue(ControlValue value) {
        return switch (value) {
            case ControlValue.Toggle toggle -> new HsControl.Value.Toggle(toggle.enabled());
            case ControlValue.Level level -> new HsControl.Value.Level(level.value());
            case ControlValue.Choice choice -> new HsControl.Value.Choice(choice.optionId());
            case ControlValue.Curve curve -> new HsControl.Value.Curve(curve.points());
        };
    }

    // ---- 失败与结果 ----

    public static DeviceFailure toFailure(HsControl.Failure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> DeviceFailure.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> DeviceFailure.PERMISSION_DENIED;
            case LOCATION_DISABLED -> DeviceFailure.LOCATION_DISABLED;
            case BLE_UNSUPPORTED -> DeviceFailure.BLE_UNSUPPORTED;
            case SCAN_THROTTLED -> DeviceFailure.SCAN_THROTTLED;
            // 扫描启动期的四种细分原因在 APP 侧只有「扫描失败」一种呈现语义，
            // 逐分支显式合并，可审计，不用 default 吞掉。
            case SCAN_START_FAILED -> DeviceFailure.SCAN_FAILED;
            case SCAN_FEATURE_UNSUPPORTED -> DeviceFailure.SCAN_FAILED;
            case SCAN_OUT_OF_RESOURCES -> DeviceFailure.SCAN_FAILED;
            case SCAN_INTERNAL_ERROR -> DeviceFailure.SCAN_FAILED;
            // SDK 已释放：会话不可能就绪。它可能发生在扫描也可能发生在写入路径，
            // 因此归到 NOT_READY，而不是伪装成扫描失败。
            case RELEASED -> DeviceFailure.NOT_READY;
            case NOT_READY -> DeviceFailure.NOT_READY;
            case TIMEOUT -> DeviceFailure.TIMEOUT;
            case REJECTED -> DeviceFailure.REJECTED;
            case UNSUPPORTED_CAPABILITY -> DeviceFailure.UNSUPPORTED_CAPABILITY;
            case DEVICE_ERROR -> DeviceFailure.DEVICE_ERROR;
            case INTERNAL_ERROR -> DeviceFailure.INTERNAL_ERROR;
        };
    }

    public static DeviceWriteResult toWriteResult(HsControl.Outcome<Void> result) {
        return switch (result) {
            case HsControl.Outcome.Ok<Void> ignored -> new DeviceWriteResult.Ok();
            case HsControl.Outcome.Err<Void> failure -> new DeviceWriteResult.Failed(toFailure(failure.failure()));
        };
    }

    // ---- 动作 ----

    public static HsControl.Action toHsAction(DeviceAction action) {
        return switch (action) {
            case DeviceAction.FindEar findEar -> new HsControl.Action.FindEar(toSide(findEar.side()));
            case DeviceAction.StopFindAlert ignored -> new HsControl.Action.StopFindAlert();
            case DeviceAction.Reboot ignored -> new HsControl.Action.Reboot();
            case DeviceAction.Rename rename -> new HsControl.Action.Rename(rename.name());
            case DeviceAction.ClearPairing ignored -> new HsControl.Action.ClearPairing();
            case DeviceAction.ResetControl ignored -> new HsControl.Action.ResetControl();
            case DeviceAction.StartRecord start -> new HsControl.Action.StartRecord(toRecordMode(start.mode()));
            case DeviceAction.StopRecord stop -> new HsControl.Action.StopRecord(toRecordMode(stop.mode()));
            case DeviceAction.ReadRecordList ignored -> new HsControl.Action.ReadRecordList();
            case DeviceAction.TransferRecord transfer ->
                    new HsControl.Action.TransferRecord(transfer.fileId(), transfer.start());
            case DeviceAction.ReadPairedDevices ignored -> new HsControl.Action.ReadPairedDevices();
            case DeviceAction.ForgetPairedDevice forget ->
                    new HsControl.Action.ForgetPairedDevice(forget.address());
            case DeviceAction.SetPriorityPairedDevice priority ->
                    new HsControl.Action.SetPriorityPairedDevice(priority.address());
            case DeviceAction.ConnectPairedDevice connect ->
                    new HsControl.Action.ConnectPairedDevice(connect.address());
            case DeviceAction.DisconnectPairedDevice disconnect ->
                    new HsControl.Action.DisconnectPairedDevice(disconnect.address());
        };
    }

    private static HsControl.Action.FindEar.Side toSide(DeviceAction.FindEar.Side side) {
        return switch (side) {
            case LEFT -> HsControl.Action.FindEar.Side.LEFT;
            case RIGHT -> HsControl.Action.FindEar.Side.RIGHT;
            case BOTH -> HsControl.Action.FindEar.Side.BOTH;
        };
    }

    private static HsControl.Action.RecordMode toRecordMode(DeviceAction.RecordMode mode) {
        return switch (mode) {
            case LOCAL -> HsControl.Action.RecordMode.LOCAL;
            case STREAM -> HsControl.Action.RecordMode.STREAM;
        };
    }

    // ---- 双连接与录音 ----

    public static PairedDeviceView toPairedDeviceView(HsControl.Paired device) {
        return new PairedDeviceView(
                device.name(),
                device.address(),
                toPairedDeviceType(device.type()),
                device.connected(),
                toPairedBusiness(device.business()),
                device.currentDevice(),
                device.priority());
    }

    public static List<PairedDeviceView> toPairedDeviceViews(List<HsControl.Paired> devices) {
        List<PairedDeviceView> mapped = new ArrayList<>(devices.size());
        for (HsControl.Paired device : devices) {
            mapped.add(toPairedDeviceView(device));
        }
        return List.copyOf(mapped);
    }

    public static PairedDeviceView.PairedDeviceType toPairedDeviceType(
            HsControl.Paired.Kind type) {
        return switch (type) {
            case OTHER -> PairedDeviceView.PairedDeviceType.OTHER;
            case PHONE -> PairedDeviceView.PairedDeviceType.PHONE;
            case COMPUTER -> PairedDeviceView.PairedDeviceType.COMPUTER;
            case PAD -> PairedDeviceView.PairedDeviceType.PAD;
            case TV -> PairedDeviceView.PairedDeviceType.TV;
            case WEARABLE -> PairedDeviceView.PairedDeviceType.WEARABLE;
        };
    }

    public static PairedDeviceView.PairedBusiness toPairedBusiness(
            HsControl.Paired.Activity business) {
        return switch (business) {
            case IDLE -> PairedDeviceView.PairedBusiness.IDLE;
            case MUSIC -> PairedDeviceView.PairedBusiness.MUSIC;
            case CALL -> PairedDeviceView.PairedBusiness.CALL;
        };
    }

    public static DualConnectionView toDualConnectionView(HsControl.Dual state) {
        return new DualConnectionView(
                state.dualEnabled(),
                state.priorityAddress(),
                toPairedDeviceViews(state.devices()));
    }

    public static RecordFileView toRecordFileView(HsControl.Recording file) {
        return new RecordFileView(file.fileId(), file.name(), file.durationSeconds(), file.sizeBytes());
    }

    public static List<RecordFileView> toRecordFileViews(List<HsControl.Recording> files) {
        List<RecordFileView> mapped = new ArrayList<>(files.size());
        for (HsControl.Recording file : files) {
            mapped.add(toRecordFileView(file));
        }
        return List.copyOf(mapped);
    }
}