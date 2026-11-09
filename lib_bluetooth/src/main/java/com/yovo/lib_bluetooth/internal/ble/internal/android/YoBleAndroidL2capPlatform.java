package com.yovo.lib_bluetooth.internal.ble.internal.android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.channel.YoBleL2capPlatform;
import com.yovo.lib_bluetooth.internal.ble.internal.android.compat.YoBleL2capCompat;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Android 10+ (API 29+) L2CAP CoC Socket 平台适配器（支持 connect 阻塞期间可取消关闭 Socket）。
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidL2capPlatform implements YoBleL2capPlatform {

    private final YoBluetoothAdapterProvider adapterProvider;

    public YoBleAndroidL2capPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider) {
        this.adapterProvider = adapterProvider;
    }

    @NonNull
    @Override
    public SocketConnection openClientChannel(@NonNull BleDeviceId deviceId,
                                              int psm,
                                              boolean secure) throws IOException {
        return openClientChannel(deviceId, psm, secure, null);
    }

    @NonNull
    @Override
    public SocketConnection openClientChannel(@NonNull BleDeviceId deviceId,
                                              int psm,
                                              boolean secure,
                                              @Nullable Consumer<Closeable> cancelHookConsumer)
            throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new IOException("L2CAP CoC requires API 29+");
        }
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            throw new IOException("BluetoothAdapter unavailable");
        }
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid device address: " + deviceId, e);
        }
        BluetoothSocket socket = YoBleL2capCompat.createClientSocket(device, psm, secure);
        if (cancelHookConsumer != null) {
            cancelHookConsumer.accept(socket::close);
        }
        InputStream in;
        OutputStream out;
        try {
            socket.connect();
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (Throwable t) {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("L2CAP connect failed: " + t.getMessage(), t);
        }

        return new SocketConnection() {
            @Override
            public int read(@NonNull byte[] buffer) throws IOException {
                return in.read(buffer);
            }

            @Override
            public void write(@NonNull byte[] data) throws IOException {
                out.write(data);
                out.flush();
            }

            @Override
            public void close() throws IOException {
                socket.close();
            }
        };
    }
}
