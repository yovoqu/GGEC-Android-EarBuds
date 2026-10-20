package com.yovo.lib_ai.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 连接、超时、取消或套接字中断。响应体尚未按协议解析。
 */
public final class YoAiTransportException extends YoAiException {

    private final boolean cancelled;

    public YoAiTransportException(@Nullable String message, @Nullable Throwable cause) {
        this(message, cause, false);
    }

    private YoAiTransportException(@Nullable String message, @Nullable Throwable cause, boolean cancelled) {
        super(message, cause);
        this.cancelled = cancelled;
    }

    /**
     * 调用方取消或客户端关闭造成的中断，不是网络故障。
     */
    @NonNull
    public static YoAiTransportException cancelled(@Nullable Throwable cause) {
        return new YoAiTransportException("Call cancelled", cause, true);
    }

    public boolean cancelled() {
        return cancelled;
    }
}
