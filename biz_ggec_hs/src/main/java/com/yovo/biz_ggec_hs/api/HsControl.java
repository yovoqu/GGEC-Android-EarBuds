package com.yovo.biz_ggec_hs.api;

import com.yovo.biz_ggec_hs.api.HsDevice.Info;
import com.yovo.biz_ggec_hs.api.HsDevice.Radios;
import com.yovo.biz_ggec_hs.api.HsDevice.Snapshot;

import java.util.List;
import java.util.Map;

/**
 * 连接与设备交互。
 *
 * <p>写入和 {@link Action} 是一发一收：本端写命令，设备回一帧。
 * {@link Listener#onDeviceEvent} 是设备主动上报，本端不回。</p>
 */
public final class HsControl {

    private HsControl() {
    }

    /** 当前连接的双模身份。GATT 只走 {@code radios.bleAddress()}。 */
    public record Peer(String modelId, Radios radios) {
        public static Peer none() {
            return new Peer("", Radios.none());
        }
    }

    public enum State {
        IDLE, CONNECTING, READY, DISCONNECTED
    }

    public sealed interface Value {
        record Toggle(boolean enabled) implements Value {
        }

        record Level(int value) implements Value {
        }

        record Choice(String optionId) implements Value {
        }

        record Curve(List<Integer> points) implements Value {
        }
    }

    public sealed interface Action {
        enum RecordMode { LOCAL, STREAM }

        record FindEar(Side side) implements Action {
            public enum Side { LEFT, RIGHT, BOTH }
        }

        record StopFindAlert() implements Action {
        }

        record Reboot() implements Action {
        }

        record Rename(String name) implements Action {
        }

        record ClearPairing() implements Action {
        }

        record ResetControl() implements Action {
        }

        record StartRecord(RecordMode mode) implements Action {
        }

        record StopRecord(RecordMode mode) implements Action {
        }

        record ReadRecordList() implements Action {
        }

        record TransferRecord(String fileId, boolean start) implements Action {
        }

        record ReadPairedDevices() implements Action {
        }

        record ForgetPairedDevice(String address) implements Action {
        }

        record SetPriorityPairedDevice(String address) implements Action {
        }

        record ConnectPairedDevice(String address) implements Action {
        }

        record DisconnectPairedDevice(String address) implements Action {
        }
    }

    public interface Callback {
        void onResult(Outcome<Void> result);
    }

    public sealed interface Outcome<T> {
        record Ok<T>(T value) implements Outcome<T> {
        }

        record Err<T>(Failure failure) implements Outcome<T> {
        }
    }

    public enum Failure {
        BLUETOOTH_DISABLED, PERMISSION_DENIED, LOCATION_DISABLED, BLE_UNSUPPORTED,
        SCAN_THROTTLED, SCAN_START_FAILED, SCAN_FEATURE_UNSUPPORTED, SCAN_OUT_OF_RESOURCES,
        SCAN_INTERNAL_ERROR, RELEASED,
        NOT_READY, TIMEOUT, REJECTED, UNSUPPORTED_CAPABILITY, DEVICE_ERROR, INTERNAL_ERROR
    }

    public interface Listener {
        default void onConnectionChanged(State state, Failure failure) {
        }

        default void onSnapshotChanged(Snapshot snapshot) {
        }

        default void onDeviceEvent(Event event) {
        }
    }

    public sealed interface Event {
        record PairedDevicesChanged(List<Paired> devices) implements Event {
        }

        record DualConnectionChanged(Dual state) implements Event {
        }

        record RecordFilesChanged(List<Recording> files) implements Event {
        }

        record DeviceInfoChanged(Info info) implements Event {
        }

        record ServiceStatusChanged(Map<String, String> status) implements Event {
        }

        record RemoteStreamStop() implements Event {
        }

        record FindAlertChanged(int code, int state) implements Event {
        }

        record BeginnerModeChanged(int earType, int actionType) implements Event {
        }

        record EarExchangeChanged(int earType) implements Event {
        }

        record VoiceAssistantChanged(int code, int status) implements Event {
        }
    }

    public record Paired(String name, String address, Kind type, boolean connected, Activity business,
                         boolean currentDevice, boolean priority) {
        public enum Kind { OTHER, PHONE, COMPUTER, PAD, TV, WEARABLE }

        public enum Activity { IDLE, MUSIC, CALL }
    }

    public record Recording(String fileId, String name, int durationSeconds, long sizeBytes) {
    }

    public record Dual(boolean dualEnabled, String priorityAddress, List<Paired> devices) {
    }
}
