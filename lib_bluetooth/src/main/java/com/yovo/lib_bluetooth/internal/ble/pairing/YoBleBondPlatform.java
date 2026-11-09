package com.yovo.lib_bluetooth.internal.ble.pairing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;

import java.util.List;

/**
 * BLE 配对平台适配端口（隔离 BluetoothDevice.createBond / removeBond 与 Fake 替身）。
 */
public interface YoBleBondPlatform {

    interface ActionResult {
        boolean success();

        @Nullable
        BleErrorCode errorCode();

        @Nullable
        String detail();

        static ActionResult ok() {
            return new Simple(true, null, null);
        }

        static ActionResult fail(@NonNull BleErrorCode code, @NonNull String detail) {
            return new Simple(false, code, detail);
        }

        final class Simple implements ActionResult {
            private final boolean ok;
            private final BleErrorCode code;
            private final String detail;

            Simple(boolean ok, @Nullable BleErrorCode code, @Nullable String detail) {
                this.ok = ok;
                this.code = code;
                this.detail = detail;
            }

            @Override
            public boolean success() {
                return ok;
            }

            @Nullable
            @Override
            public BleErrorCode errorCode() {
                return code;
            }

            @Nullable
            @Override
            public String detail() {
                return detail;
            }
        }
    }

    @NonNull
    List<BleDeviceIdentity> getBondedDevices();

    @NonNull
    BleBondState getBondState(@NonNull BleDeviceId deviceId);

    @NonNull
    ActionResult createBond(@NonNull BleDeviceId deviceId);

    @NonNull
    ActionResult cancelBondProcess(@NonNull BleDeviceId deviceId);

    @NonNull
    ActionResult removeBond(@NonNull BleDeviceId deviceId, boolean allowHiddenApi);
}
