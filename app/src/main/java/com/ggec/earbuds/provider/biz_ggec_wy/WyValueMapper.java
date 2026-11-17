package com.ggec.earbuds.provider.biz_ggec_wy;

import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code biz_ggec_wy} 契约 ↔ APP 领域词汇映射点。
 */
public final class WyValueMapper {

    private WyValueMapper() {
    }

    public static WyDevice.Capability capabilityOf(String capabilityId) {
        if (capabilityId == null) {
            throw new IllegalArgumentException("capability id must not be null");
        }
        return WyDevice.Capability.valueOf(capabilityId);
    }

    public static DeviceCapabilityView toCapabilityView(WyDevice.Feature descriptor) {
        return new DeviceCapabilityView(
                descriptor.capability().name(),
                descriptor.title(),
                toGroup(descriptor.group()),
                toKind(descriptor.kind()),
                toOptions(descriptor.options()),
                toRange(descriptor.range()));
    }

    public static DeviceCapabilityGroup toGroup(WyDevice.Group group) {
        return switch (group) {
            case CONTROL -> DeviceCapabilityGroup.CONTROL;
            case RECORD -> DeviceCapabilityGroup.RECORD;
            case ABOUT -> DeviceCapabilityGroup.ABOUT;
        };
    }

    public static DeviceCapabilityKind toKind(WyDevice.Form kind) {
        return switch (kind) {
            case TOGGLE -> DeviceCapabilityKind.TOGGLE;
            case LEVEL -> DeviceCapabilityKind.LEVEL;
            case CHOICE -> DeviceCapabilityKind.CHOICE;
            case ACTION -> DeviceCapabilityKind.ACTION;
            case GATE -> DeviceCapabilityKind.GATE;
        };
    }

    public static List<CapabilityOption> toOptions(List<WyDevice.Option> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<CapabilityOption> mapped = new ArrayList<>(options.size());
        for (WyDevice.Option opt : options) {
            mapped.add(new CapabilityOption(opt.optionId(), opt.title()));
        }
        return List.copyOf(mapped);
    }

    public static Optional<ControlRange> toRange(Optional<WyDevice.Range> range) {
        return range.map(r -> new ControlRange(r.min(), r.max(), r.step()));
    }

    public static DeviceFailure toFailure(WyControl.Failure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> DeviceFailure.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> DeviceFailure.PERMISSION_DENIED;
            case SCAN_FAILED -> DeviceFailure.SCAN_FAILED;
            case TIMEOUT -> DeviceFailure.TIMEOUT;
            case DISCONNECTED -> DeviceFailure.INTERNAL_ERROR;
            case REJECTED -> DeviceFailure.REJECTED;
            case UNSUPPORTED_CAPABILITY -> DeviceFailure.UNSUPPORTED_CAPABILITY;
            case DEVICE_ERROR -> DeviceFailure.DEVICE_ERROR;
            case INTERNAL_ERROR -> DeviceFailure.INTERNAL_ERROR;
            case CLASSIC_NOT_CONNECTED -> DeviceFailure.CLASSIC_NOT_CONNECTED;
        };
    }

    public static WyControl.Value toWyControlValue(ControlValue value) {
        if (value instanceof ControlValue.Toggle t) {
            return new WyControl.Value.Toggle(t.enabled());
        } else if (value instanceof ControlValue.Level l) {
            return new WyControl.Value.Level(l.value());
        } else if (value instanceof ControlValue.Choice c) {
            return new WyControl.Value.Choice(c.optionId());
        }
        throw new IllegalArgumentException("Unsupported control value: " + value);
    }

    public static WyControl.Action toWyAction(DeviceAction action) {
        if (action instanceof DeviceAction.Reboot) {
            return new WyControl.Action.Reboot();
        } else if (action instanceof DeviceAction.Rename r) {
            return new WyControl.Action.Rename(r.name());
        }
        throw new IllegalArgumentException("Unsupported device action: " + action);
    }

    public static DeviceWriteResult toWriteResult(WyControl.Outcome<?> outcome) {
        if (outcome instanceof WyControl.Outcome.Ok) {
            return new DeviceWriteResult.Ok();
        } else if (outcome instanceof WyControl.Outcome.Err err) {
            return new DeviceWriteResult.Failed(toFailure(err.failure()));
        }
        return new DeviceWriteResult.Failed(DeviceFailure.INTERNAL_ERROR);
    }
}
