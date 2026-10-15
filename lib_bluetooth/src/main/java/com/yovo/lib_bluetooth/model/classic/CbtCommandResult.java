package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SPP 命令执行结果。
 */
public final class CbtCommandResult {

    private final boolean success;
    @Nullable
    private final byte[] response;
    @Nullable
    private final String message;

    public CbtCommandResult(boolean success,
                            @Nullable byte[] response,
                            @Nullable String message) {
        this.success = success;
        this.response = response;
        this.message = message;
    }

    public boolean success() {
        return success;
    }

    @Nullable
    public byte[] response() {
        return response;
    }

    @Nullable
    public String message() {
        return message;
    }
}
