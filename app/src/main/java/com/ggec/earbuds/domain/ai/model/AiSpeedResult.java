package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 平台页测速的结果：只关心首字。{@code fingerprint} 是测速时的配置摘要（接口地址、协议、API Key），
 * 为空表示旧数据（不判过期）。
 */
public record AiSpeedResult(
        String modelId,
        Status status,
        long firstTokenMillis,
        long totalMillis,
        @Nullable AiFailureKind failureKind,
        @Nullable String message,
        long testedAtMillis,
        int outputTokens,
        @Nullable String fingerprint) {

    public enum Status {
        OK,
        FAILED,
        TIMEOUT
    }

    public AiSpeedResult(String modelId, Status status, long firstTokenMillis, long totalMillis,
                         @Nullable AiFailureKind failureKind, @Nullable String message,
                         long testedAtMillis) {
        this(modelId, status, firstTokenMillis, totalMillis, failureKind, message, testedAtMillis, -1, null);
    }

    public AiSpeedResult(String modelId, Status status, long firstTokenMillis, long totalMillis,
                         @Nullable AiFailureKind failureKind, @Nullable String message,
                         long testedAtMillis, int outputTokens) {
        this(modelId, status, firstTokenMillis, totalMillis, failureKind, message, testedAtMillis, outputTokens,
                null);
    }

    public AiSpeedResult {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(status, "status");
        fingerprint = fingerprint == null || fingerprint.isEmpty() ? null : fingerprint;
    }

    public boolean ok() {
        return status == Status.OK;
    }

    /** 失败分类；超时统一归为 {@link AiFailureKind#TIMEOUT}。成功时为空。 */
    @Nullable
    public AiFailureKind effectiveFailure() {
        if (ok()) {
            return null;
        }
        if (status == Status.TIMEOUT) {
            return AiFailureKind.TIMEOUT;
        }
        return failureKind == null ? AiFailureKind.UNKNOWN : failureKind;
    }

    public AiSpeedResult stamped(long testedAt, @Nullable String newFingerprint) {
        return new AiSpeedResult(modelId, status, firstTokenMillis, totalMillis, failureKind, message, testedAt,
                outputTokens, newFingerprint);
    }

    public int tokensPerSecond() {
        if (!ok() || outputTokens <= 0 || totalMillis <= firstTokenMillis) {
            return -1;
        }
        long generating = totalMillis - firstTokenMillis;
        return (int) Math.round(outputTokens * 1000d / generating);
    }
}
