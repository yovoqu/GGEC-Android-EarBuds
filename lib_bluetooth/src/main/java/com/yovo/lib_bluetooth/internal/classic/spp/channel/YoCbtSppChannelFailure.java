package com.yovo.lib_bluetooth.internal.classic.spp.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description RFCOMM 通道连接尝试的结构化失败信息。
 */
public final class YoCbtSppChannelFailure {
    public enum Reason {
        HIDDEN_API_NOT_AVAILABLE,
        HIDDEN_API_BLOCKED,
        REFLECTION_FAILED,
        INVALID_CHANNEL,
        SOCKET_CREATE_FAILED,
        CONNECT_FAILED,
        UNSUPPORTED_ON_ROM
    }

    private final Reason reason;
    private final int channel;
    private final String message;
    private final Throwable cause;

    public YoCbtSppChannelFailure(@NonNull Reason reason, int channel,
                                   @NonNull String message, @Nullable Throwable cause) {
        this.reason = reason;
        this.channel = channel;
        this.message = message;
        this.cause = cause;
    }

    @NonNull public Reason getReason() { return reason; }
    public int getChannel() { return channel; }
    @NonNull public String getMessage() { return message; }
    @Nullable public Throwable getCause() { return cause; }
}
