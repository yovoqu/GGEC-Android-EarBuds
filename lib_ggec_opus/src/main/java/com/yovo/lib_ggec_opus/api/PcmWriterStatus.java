package com.yovo.lib_ggec_opus.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

/**
 * PCM 落盘写入器的状态分类。
 *
 * <p>写入器的失败不再是「静默吞掉」：打开失败、写失败、关闭失败各自是一个分类，
 * 调用方可在任意时刻读取 {@link YoPcmWriter#status()} 判定当前是否还能写入，
 * 并把 {@link #detail()} 带进自己的日志/诊断。</p>
 *
 * <p>语义：失败是**粘滞**的——首次失败后状态保持，后续写入直接跳过，
 * 不会把「文件已损坏」误报成「仍在正常写入」。</p>
 */
public final class PcmWriterStatus {

    /** 状态分类。 */
    public enum Code {
        /** 文件已打开，可写入。 */
        OPEN,
        /** 构造期打开失败（目录不可建、无权限、目标是目录等）。 */
        OPEN_FAILED,
        /** 写入失败（磁盘满、句柄失效等）。 */
        WRITE_FAILED,
        /** 冲刷/关闭失败。 */
        CLOSE_FAILED,
        /** 已正常关闭。 */
        CLOSED
    }

    @NonNull private final Code code;
    @Nullable private final String detail;

    private PcmWriterStatus(@NonNull Code code, @Nullable String detail) {
        this.code = code;
        this.detail = detail;
    }

    @NonNull
    public static PcmWriterStatus open() {
        return new PcmWriterStatus(Code.OPEN, null);
    }

    @NonNull
    public static PcmWriterStatus openFailed(@NonNull IOException cause) {
        return new PcmWriterStatus(Code.OPEN_FAILED, describe(cause));
    }

    @NonNull
    public static PcmWriterStatus writeFailed(@NonNull IOException cause) {
        return new PcmWriterStatus(Code.WRITE_FAILED, describe(cause));
    }

    @NonNull
    public static PcmWriterStatus closeFailed(@NonNull IOException cause) {
        return new PcmWriterStatus(Code.CLOSE_FAILED, describe(cause));
    }

    @NonNull
    public static PcmWriterStatus closed() {
        return new PcmWriterStatus(Code.CLOSED, null);
    }

    @NonNull
    public Code code() {
        return code;
    }

    /** 失败详情（异常类型 + 消息）；非失败状态为 {@code null}。 */
    @Nullable
    public String detail() {
        return detail;
    }

    /** 当前状态是否仍可接收写入。 */
    public boolean isWritable() {
        return code == Code.OPEN;
    }

    /** 是否处于失败分类（打开/写入/关闭任一失败）。 */
    public boolean isFailure() {
        return code == Code.OPEN_FAILED
                || code == Code.WRITE_FAILED
                || code == Code.CLOSE_FAILED;
    }

    @NonNull
    private static String describe(@NonNull IOException cause) {
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    @NonNull
    @Override
    public String toString() {
        return detail == null ? code.name() : code.name() + "(" + detail + ")";
    }
}
