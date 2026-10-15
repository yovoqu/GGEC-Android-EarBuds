package com.yovo.lib_bluetooth.internal.classic.spp.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP I/O 操作的结构化失败信息。
 */
public final class YoCbtSppIoFailure {
    public enum Operation { READ, WRITE, FLUSH, CLOSE }

    private final Operation operation;
    private final int bytesProcessed;
    private final String message;
    private final Throwable cause;

    public YoCbtSppIoFailure(@NonNull Operation operation, int bytesProcessed,
                              @NonNull String message, @Nullable Throwable cause) {
        this.operation = operation;
        this.bytesProcessed = bytesProcessed;
        this.message = message;
        this.cause = cause;
    }

    @NonNull public Operation getOperation() { return operation; }
    public int getBytesProcessed() { return bytesProcessed; }
    @NonNull public String getMessage() { return message; }
    @Nullable public Throwable getCause() { return cause; }
}
