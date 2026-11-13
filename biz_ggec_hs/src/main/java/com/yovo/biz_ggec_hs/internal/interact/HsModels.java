package com.yovo.biz_ggec_hs.internal.interact;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolDual;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolInfo;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolPaired;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolRecording;

import java.util.ArrayList;
import java.util.List;

/** 协议记录到对外契约的唯一转换。 */
public final class HsModels {

    private HsModels() {
    }

    public static HsControl.Paired paired(ProtocolPaired device) {
        return new HsControl.Paired(
                device.name(),
                device.address(),
                kind(device.kind()),
                device.connected(),
                activity(device.activity()),
                device.currentDevice(),
                device.priority());
    }

    public static List<HsControl.Paired> paired(List<ProtocolPaired> devices) {
        List<HsControl.Paired> copy = new ArrayList<>(devices.size());
        for (ProtocolPaired device : devices) {
            copy.add(paired(device));
        }
        return List.copyOf(copy);
    }

    public static HsControl.Dual dual(ProtocolDual state) {
        return new HsControl.Dual(state.dualEnabled(), state.priorityAddress(), paired(state.devices()));
    }

    public static HsControl.Recording recording(ProtocolRecording file) {
        return new HsControl.Recording(
                file.fileId(), file.name(), file.durationSeconds(), file.sizeBytes());
    }

    public static List<HsControl.Recording> recordings(List<ProtocolRecording> files) {
        List<HsControl.Recording> copy = new ArrayList<>(files.size());
        for (ProtocolRecording file : files) {
            copy.add(recording(file));
        }
        return List.copyOf(copy);
    }

    /** 三项都未上报时返回 null。 */
    public static HsDevice.Info info(ProtocolInfo facts) {
        if (facts == null
                || (facts.firmware() == null && facts.earColor() == null && facts.earInCase() == null)) {
            return null;
        }
        return new HsDevice.Info(
                facts.firmware() == null ? HsDevice.Info.UNKNOWN_FIRMWARE : facts.firmware(),
                facts.earColor() == null ? HsDevice.Info.UNKNOWN_EAR_COLOR : facts.earColor(),
                ear(facts.earInCase()));
    }

    public static HsDevice.Ear ear(Integer status) {
        if (status == null) {
            return HsDevice.Ear.UNKNOWN;
        }
        return switch (status) {
            case 0 -> HsDevice.Ear.NONE;
            case 1 -> HsDevice.Ear.LEFT;
            case 2 -> HsDevice.Ear.RIGHT;
            case 3 -> HsDevice.Ear.BOTH;
            default -> throw new IllegalArgumentException("耳机在仓状态超出协议范围: " + status);
        };
    }

    private static HsControl.Paired.Kind kind(ProtocolPaired.Kind kind) {
        return switch (kind) {
            case OTHER -> HsControl.Paired.Kind.OTHER;
            case PHONE -> HsControl.Paired.Kind.PHONE;
            case COMPUTER -> HsControl.Paired.Kind.COMPUTER;
            case PAD -> HsControl.Paired.Kind.PAD;
            case TV -> HsControl.Paired.Kind.TV;
            case WEARABLE -> HsControl.Paired.Kind.WEARABLE;
        };
    }

    private static HsControl.Paired.Activity activity(ProtocolPaired.Activity activity) {
        return switch (activity) {
            case IDLE -> HsControl.Paired.Activity.IDLE;
            case MUSIC -> HsControl.Paired.Activity.MUSIC;
            case CALL -> HsControl.Paired.Activity.CALL;
        };
    }
}
