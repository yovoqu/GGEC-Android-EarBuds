package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/** APP 能发起的动作：类型化，不含协议字节。 */
public sealed interface DeviceAction {

    /** 录音模式：本地录音 / 随录随传。与 biz {@code HsControl.Action.RecordMode} 一一对应。 */
    enum RecordMode {
        LOCAL,
        STREAM
    }

    record FindEar(Side side) implements DeviceAction {
        public FindEar {
            Objects.requireNonNull(side, "side");
        }

        public enum Side {
            LEFT,
            RIGHT,
            BOTH
        }
    }

    record StopFindAlert() implements DeviceAction {
    }

    record Reboot() implements DeviceAction {
    }

    record Rename(String name) implements DeviceAction {
        public Rename {
            Objects.requireNonNull(name, "name");
        }
    }

    record ClearPairing() implements DeviceAction {
    }

    /** 重置手势配置到出厂绑定。 */
    record ResetControl() implements DeviceAction {
    }

    record StartRecord(RecordMode mode) implements DeviceAction {
        public StartRecord {
            Objects.requireNonNull(mode, "mode");
        }
    }

    record StopRecord(RecordMode mode) implements DeviceAction {
        public StopRecord {
            Objects.requireNonNull(mode, "mode");
        }
    }

    record ReadRecordList() implements DeviceAction {
    }

    record TransferRecord(String fileId, boolean start) implements DeviceAction {
        public TransferRecord {
            Objects.requireNonNull(fileId, "fileId");
        }
    }

    record ReadPairedDevices() implements DeviceAction {
    }

    record ForgetPairedDevice(String address) implements DeviceAction {
        public ForgetPairedDevice {
            Objects.requireNonNull(address, "address");
        }
    }

    record SetPriorityPairedDevice(String address) implements DeviceAction {
        public SetPriorityPairedDevice {
            Objects.requireNonNull(address, "address");
        }
    }

    record ConnectPairedDevice(String address) implements DeviceAction {
        public ConnectPairedDevice {
            Objects.requireNonNull(address, "address");
        }
    }

    record DisconnectPairedDevice(String address) implements DeviceAction {
        public DisconnectPairedDevice {
            Objects.requireNonNull(address, "address");
        }
    }
}
