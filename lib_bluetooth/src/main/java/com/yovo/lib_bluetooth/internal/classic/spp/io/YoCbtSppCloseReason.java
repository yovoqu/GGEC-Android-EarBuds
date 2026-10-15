package com.yovo.lib_bluetooth.internal.classic.spp.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 连接关闭原因，区分本地/远端断开、失败及资源释放清理。
 */
public final class YoCbtSppCloseReason {
    public enum Kind {
        LOCAL_DISCONNECT,
        REMOTE_DISCONNECT,
        FAILURE,
        RELEASED,
        TIMEOUT
    }

    private final Kind kind;
    private final String message;
    private final YoCbtFailure failure;

    public YoCbtSppCloseReason(@NonNull Kind kind, @NonNull String message,
                                @Nullable YoCbtFailure failure) {
        this.kind = kind;
        this.message = message;
        this.failure = failure;
    }

    @NonNull public Kind getKind() { return kind; }
    @NonNull public String getMessage() { return message; }
    @Nullable public YoCbtFailure getFailure() { return failure; }

    public boolean isLocal() { return kind == Kind.LOCAL_DISCONNECT || kind == Kind.RELEASED; }
    public boolean isRemote() { return kind == Kind.REMOTE_DISCONNECT; }
    public boolean isError() { return kind == Kind.FAILURE || kind == Kind.TIMEOUT; }

    @NonNull
    public static YoCbtSppCloseReason local(@NonNull String message) {
        return new YoCbtSppCloseReason(Kind.LOCAL_DISCONNECT, message, null);
    }

    @NonNull
    public static YoCbtSppCloseReason remote(@NonNull String message) {
        return new YoCbtSppCloseReason(Kind.REMOTE_DISCONNECT, message, null);
    }

    @NonNull
    public static YoCbtSppCloseReason failure(@NonNull YoCbtFailure failure) {
        return new YoCbtSppCloseReason(Kind.FAILURE, failure.getMessage(), failure);
    }

    @NonNull
    public static YoCbtSppCloseReason released() {
        return new YoCbtSppCloseReason(Kind.RELEASED, "Client released", null);
    }

    @NonNull
    public static YoCbtSppCloseReason timeout() {
        return new YoCbtSppCloseReason(Kind.TIMEOUT, "Connection timeout", null);
    }
}
