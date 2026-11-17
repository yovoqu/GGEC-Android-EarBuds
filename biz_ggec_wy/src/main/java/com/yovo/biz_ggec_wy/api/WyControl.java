package com.yovo.biz_ggec_wy.api;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.api.WyDevice.Battery;
import com.yovo.biz_ggec_wy.api.WyDevice.Info;
import com.yovo.biz_ggec_wy.api.WyDevice.Radios;
import com.yovo.biz_ggec_wy.api.WyDevice.Snapshot;

import java.util.List;

/**
 * WY 连接与设备交互。
 */
public final class WyControl {

    private WyControl() {
    }

    /** 当前连接的对端身份。 */
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
    }

    public sealed interface Action {
        record StartStreamRecord() implements Action {
        }

        record StopStreamRecord() implements Action {
        }

        record Reboot() implements Action {
        }

        record Rename(String name) implements Action {
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
        BLUETOOTH_DISABLED, PERMISSION_DENIED, SCAN_FAILED, TIMEOUT, DISCONNECTED,
        REJECTED, UNSUPPORTED_CAPABILITY, DEVICE_ERROR, INTERNAL_ERROR, CLASSIC_NOT_CONNECTED
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
        record BatteryChanged(Battery battery) implements Event {
        }

        record DeviceInfoChanged(Info info) implements Event {
        }

        record RemoteStreamStop() implements Event {
        }
    }
}
