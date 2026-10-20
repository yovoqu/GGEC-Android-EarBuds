package com.yovo.lib_ai.probe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;

/**
 * 单个模型的测速结果。毫秒数从请求发出开始计，失败时为失败发生的时刻，未发生则为 -1。
 */
public final class YoProbeResult {

    public enum Status {
        OK,
        FAILED,
        TIMEOUT
    }

    private final String model;
    private final Status status;
    private final long firstTokenMillis;
    private final long totalMillis;
    @Nullable
    private final String message;
    @Nullable
    private final YoAiErrorKind errorKind;
    private final int httpStatus;
    @Nullable
    private final String sample;

    private YoProbeResult(
            String model,
            Status status,
            long firstTokenMillis,
            long totalMillis,
            @Nullable String message,
            @Nullable YoAiErrorKind errorKind,
            int httpStatus,
            @Nullable String sample) {
        this.model = model;
        this.status = status;
        this.firstTokenMillis = firstTokenMillis;
        this.totalMillis = totalMillis;
        this.message = message;
        this.errorKind = errorKind;
        this.httpStatus = httpStatus;
        this.sample = sample;
    }

    @NonNull
    public static YoProbeResult ok(
            @NonNull String model, long firstTokenMillis, long totalMillis, @Nullable String sample) {
        return new YoProbeResult(model, Status.OK, firstTokenMillis, totalMillis, null, null, 0, sample);
    }

    @NonNull
    public static YoProbeResult failed(
            @NonNull String model,
            long elapsedMillis,
            @NonNull YoAiErrorKind kind,
            @Nullable String message,
            int httpStatus) {
        return new YoProbeResult(model, Status.FAILED, -1L, elapsedMillis, message, kind, httpStatus, null);
    }

    @NonNull
    public static YoProbeResult timeout(@NonNull String model, long timeoutMillis) {
        return new YoProbeResult(model, Status.TIMEOUT, -1L, timeoutMillis, null,
                YoAiErrorKind.TIMEOUT, 0, null);
    }

    @NonNull
    public String model() {
        return model;
    }

    @NonNull
    public Status status() {
        return status;
    }

    public boolean ok() {
        return status == Status.OK;
    }

    /**
     * 收到第一段内容（正文或思考）所用毫秒。失败或超时为 -1。
     */
    public long firstTokenMillis() {
        return firstTokenMillis;
    }

    public long totalMillis() {
        return totalMillis;
    }

    @Nullable
    public String message() {
        return message;
    }

    @Nullable
    public YoAiErrorKind errorKind() {
        return errorKind;
    }

    /**
     * 平台返回的 HTTP 状态码，非 HTTP 失败为 0。
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * 模型回复的开头一小段，仅用于确认确实收到了内容。
     */
    @Nullable
    public String sample() {
        return sample;
    }
}
