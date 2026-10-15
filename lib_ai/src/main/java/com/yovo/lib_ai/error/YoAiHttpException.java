package com.yovo.lib_ai.error;

import androidx.annotation.Nullable;

/**
 * 服务端返回了非成功 HTTP 状态。响应体已截断保存，不包含请求头。
 */
public final class YoAiHttpException extends YoAiException {

    private final int statusCode;
    @Nullable
    private final String requestId;
    @Nullable
    private final String errorType;
    @Nullable
    private final String responseBody;

    public YoAiHttpException(
            @Nullable String message,
            int statusCode,
            @Nullable String requestId,
            @Nullable String errorType,
            @Nullable String responseBody) {
        super(message, null);
        this.statusCode = statusCode;
        this.requestId = requestId;
        this.errorType = errorType;
        this.responseBody = responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    @Nullable
    public String requestId() {
        return requestId;
    }

    @Nullable
    public String errorType() {
        return errorType;
    }

    @Nullable
    public String responseBody() {
        return responseBody;
    }
}
