package com.yovo.lib_common.system.keepalive.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 保活操作结果。lib_common 不持有 UI 文案，detail 供 app 日志使用。
 */
public final class KeepAliveResult {

    public enum Code {
        SUCCESS,
        ALREADY_ACTIVE,
        NOT_ACTIVE,
        START_FAILED,
        PRECONDITION_FAILED,
        NOTIFICATION_FAILED,
        PERMISSION_DENIED,
        THROTTLED,
        ERROR
    }

    private final Code code;
    @Nullable private final String detail;

    private KeepAliveResult(@NonNull Code code, @Nullable String detail) {
        this.code = code;
        this.detail = detail;
    }

    @NonNull
    public static KeepAliveResult of(@NonNull Code code) {
        return new KeepAliveResult(code, null);
    }

    @NonNull
    public static KeepAliveResult of(@NonNull Code code, @Nullable String detail) {
        return new KeepAliveResult(code, detail);
    }

    @NonNull
    public Code code() {
        return code;
    }

    @Nullable
    public String detailMessage() {
        return detail;
    }

    public boolean isSuccess() {
        return code == Code.SUCCESS
                || code == Code.ALREADY_ACTIVE
                || code == Code.THROTTLED;
    }
}
