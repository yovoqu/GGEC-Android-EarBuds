package com.yovo.lib_bluetooth.internal.ble.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * BLE L2CAP CoC Socket 平台适配端口，封装 Android API 29+ 系统通道能力。
 */
public interface YoBleL2capPlatform {

    interface SocketConnection {
        @NonNull
        BleDeviceId remoteDeviceId();

        int read(@NonNull byte[] buffer) throws IOException;

        void write(@NonNull byte[] data) throws IOException;

        void close() throws IOException;
    }

    interface ListeningSocket {
        int psm();

        @NonNull
        SocketConnection accept() throws IOException;

        void close() throws IOException;
    }

    @NonNull
    SocketConnection openClientChannel(@NonNull BleDeviceId deviceId,
                                       int psm,
                                       boolean secure,
                                       @Nullable Consumer<Closeable> cancelHookConsumer) throws IOException;

    @NonNull
    ListeningSocket openServer(boolean secure, @Nullable Consumer<Closeable> cancelHookConsumer) throws IOException;
}
