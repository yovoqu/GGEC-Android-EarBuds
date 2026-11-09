package com.yovo.lib_bluetooth.internal.ble.internal.android.compat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.IOException;

/**
 * BLE L2CAP CoC Socket 兼容封装（Android 10 / API 29+）。
 */
@SuppressLint("MissingPermission")
public final class YoBleL2capCompat {

    private YoBleL2capCompat() {
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @NonNull
    public static BluetoothSocket createClientSocket(@NonNull BluetoothDevice device,
                                                     int psm,
                                                     boolean secure) throws IOException {
        return secure
                ? device.createL2capChannel(psm)
                : device.createInsecureL2capChannel(psm);
    }
}
