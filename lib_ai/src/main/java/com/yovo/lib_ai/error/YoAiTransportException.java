package com.yovo.lib_ai.error;

import androidx.annotation.Nullable;

/**
 * 连接、超时、取消或套接字中断。响应体尚未按协议解析。
 */
public final class YoAiTransportException extends YoAiException {

    public YoAiTransportException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
