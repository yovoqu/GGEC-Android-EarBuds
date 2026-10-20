package com.yovo.lib_ai.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InterruptedIOException;
import java.util.Locale;

/**
 * 把任意失败归到 {@link YoAiErrorKind}。分类规则只在这里写一次，调用方不再各自解析状态码。
 */
public final class YoAiErrors {

    private YoAiErrors() {
    }

    @NonNull
    public static YoAiErrorKind kindOf(@Nullable Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof YoAiHttpException http) {
                return fromHttp(http.statusCode(), http.errorType());
            }
            if (current instanceof YoAiTransportException transport) {
                return fromTransport(transport);
            }
            if (current instanceof YoAiProtocolException) {
                return YoAiErrorKind.PROTOCOL;
            }
            if (current instanceof IllegalArgumentException) {
                return YoAiErrorKind.BAD_REQUEST;
            }
            current = current.getCause();
        }
        return YoAiErrorKind.UNKNOWN;
    }

    /**
     * 同一个请求原样重发还有希望成功。
     */
    public static boolean retryable(@NonNull YoAiErrorKind kind) {
        return kind == YoAiErrorKind.RATE_LIMITED
                || kind == YoAiErrorKind.SERVER
                || kind == YoAiErrorKind.NETWORK
                || kind == YoAiErrorKind.TIMEOUT;
    }

    private static YoAiErrorKind fromHttp(int status, @Nullable String type) {
        String lowered = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (status == 402 || lowered.contains("quota") || lowered.contains("balance")
                || lowered.contains("billing")) {
            return YoAiErrorKind.QUOTA;
        }
        if (status == 401) {
            return YoAiErrorKind.AUTHENTICATION;
        }
        if (status == 403) {
            return YoAiErrorKind.PERMISSION;
        }
        if (status == 404) {
            return YoAiErrorKind.NOT_FOUND;
        }
        if (status == 408) {
            return YoAiErrorKind.TIMEOUT;
        }
        if (status == 429) {
            return YoAiErrorKind.RATE_LIMITED;
        }
        if (status >= 500) {
            return YoAiErrorKind.SERVER;
        }
        if (status >= 400) {
            return YoAiErrorKind.BAD_REQUEST;
        }
        return YoAiErrorKind.UNKNOWN;
    }

    private static YoAiErrorKind fromTransport(YoAiTransportException transport) {
        if (transport.cancelled()) {
            return YoAiErrorKind.CANCELLED;
        }
        Throwable cause = transport.getCause();
        for (int depth = 0; cause != null && depth < 8; depth++) {
            if (cause instanceof InterruptedIOException) {
                return YoAiErrorKind.TIMEOUT;
            }
            cause = cause.getCause();
        }
        return YoAiErrorKind.NETWORK;
    }
}
