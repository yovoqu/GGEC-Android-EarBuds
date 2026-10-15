package com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 隐藏 API 调用异常，携带失败原因分类。
 */

public final class YoCbtHiddenApiException extends IOException {
    public enum Reason {
        DISABLED_BY_POLICY,
        INVALID_ARGUMENT,
        METHOD_NOT_AVAILABLE,
        REFLECTION_FAILED,
        INVOCATION_FAILED
    }

    private final Reason reason;

    public YoCbtHiddenApiException(@NonNull Reason reason, @NonNull String message) {
        super(message);
        this.reason = reason;
    }

    public YoCbtHiddenApiException(@NonNull Reason reason, @NonNull String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    @NonNull
    public Reason getReason() {
        return reason;
    }
}
