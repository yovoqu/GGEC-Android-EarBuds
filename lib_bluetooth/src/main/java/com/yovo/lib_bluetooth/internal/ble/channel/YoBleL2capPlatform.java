package com.yovo.lib_bluetooth.internal.ble.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * BLE L2CAP CoC Socket 平台适配端口，封装 Android API 29+ 系统通道能力。
 *
 * <p>客户端通道与服务端监听端都是 {@link Closeable}：取消/释放路径统一按 {@code close()} 收口。</p>
 */
public interface YoBleL2capPlatform {

    interface SocketConnection extends Closeable {
        @NonNull
        BleDeviceId remoteDeviceId();

        int read(@NonNull byte[] buffer) throws IOException;

        void write(@NonNull byte[] data) throws IOException;

        @Override
        void close() throws IOException;
    }

    interface ListeningSocket extends Closeable {
        int psm();

        @NonNull
        SocketConnection accept() throws IOException;

        @Override
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
