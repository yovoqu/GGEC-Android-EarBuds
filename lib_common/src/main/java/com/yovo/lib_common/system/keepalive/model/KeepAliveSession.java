package com.yovo.lib_common.system.keepalive.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface KeepAliveSession {

    @NonNull
    String sessionId();

    boolean isActive();

    /** 当前已登记的最新通知规格（服务端与客户端共享的会话状态）。 */
    @NonNull
    KeepAliveNotificationSpec latestNotificationSpec();

    @NonNull
    KeepAliveResult update(@NonNull KeepAliveNotificationSpec spec);

    @NonNull
    KeepAliveResult syncWakeLock(boolean shouldHold);

    @NonNull
    KeepAliveResult stop();

    void setStopHandler(@Nullable KeepAliveStopHandler handler);
}
