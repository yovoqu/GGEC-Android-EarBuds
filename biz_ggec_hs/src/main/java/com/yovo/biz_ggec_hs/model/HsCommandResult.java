package com.yovo.biz_ggec_hs.model;

import androidx.annotation.Nullable;

/**
 * 一条 HS 命令的完成结果。状态码与耳机协议一致。
 */
public final class HsCommandResult {
    public static final int SUCCESS = 0;
    public static final int TIMEOUT = 1;
    public static final int RESERVED = 2;
    public static final int FAILED = 3;

    private final int code;
    private final int value;
    @Nullable
    private final String message;
    @Nullable
    private final Object data;

    public HsCommandResult(int code, int value, @Nullable String message, @Nullable Object data) {
        this.code = code;
        this.value = value;
        this.message = message;
        this.data = data;
    }

    public int code() {
        return code;
    }

    public int value() {
        return value;
    }

    @Nullable
    public String message() {
        return message;
    }

    @Nullable
    public Object data() {
        return data;
    }

    public boolean isSuccess() {
        return code == SUCCESS;
    }
}
