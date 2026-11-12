package com.yovo.biz_ggec_hs.internal.center.port;

import androidx.annotation.NonNull;

/**
 * 协议层看见的唯一链路。
 * 只传递原始字节和就绪状态，不出现 GATT、UUID、扫描或 lib_bluetooth 类型。
 */
public interface HsCommandLink {

    void open(@NonNull String macAddress);

    void close();

    void release();

    boolean write(@NonNull byte[] payload, @NonNull WriteCallback callback);

    void setListener(Listener listener);

    interface WriteCallback {
        void onStarted();

        void onCompleted(int status);

        void onTimeout();
    }

    interface Listener {
        void onReady(boolean ready);

        void onInbound(@NonNull byte[] value);

        void onLinkError(@NonNull String message);
    }
}
