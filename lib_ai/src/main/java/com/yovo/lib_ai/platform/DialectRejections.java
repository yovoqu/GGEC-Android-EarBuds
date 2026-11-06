package com.yovo.lib_ai.platform;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.models.YoCapabilityException;

/**
 * 方言层的本地拒绝工具。厂商不支持某个参数时，必须抛 {@link YoCapabilityException}，
 * 这样 {@code YoAiErrors.kindOf} 才能把失败归到“参数/能力不支持”而不是普通 400。
 */
public final class DialectRejections {

    private DialectRejections() {
    }

    @NonNull
    public static YoCapabilityException unsupportedParam(@NonNull String message) {
        return YoCapabilityException.unsupportedParam(message);
    }
}
