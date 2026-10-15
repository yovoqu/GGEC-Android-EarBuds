package com.yovo.lib_ai.error;

import androidx.annotation.Nullable;

/**
 * HTTP 已经成功，或 WebSocket 帧已经到达，但报文不符合当前协议。
 */
public final class YoAiProtocolException extends YoAiException {

    @Nullable
    private final String rawJson;

    public YoAiProtocolException(@Nullable String message, @Nullable String rawJson) {
        super(message, null);
        this.rawJson = rawJson;
    }

    public YoAiProtocolException(@Nullable String message, @Nullable String rawJson, @Nullable Throwable cause) {
        super(message, cause);
        this.rawJson = rawJson;
    }

    @Nullable
    public String rawJson() {
        return rawJson;
    }
}
