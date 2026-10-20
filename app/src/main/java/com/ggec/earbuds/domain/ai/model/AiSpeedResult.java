package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 一次测速的结果，随平台配置保存，方便下次打开设置页直接看到。
 */
public record AiSpeedResult(
        String modelId,
        Status status,
        long firstTokenMillis,
        long totalMillis,
        @Nullable AiFailureKind failureKind,
        @Nullable String message,
        long testedAtMillis) {

    public enum Status {
        OK,
        FAILED,
        TIMEOUT
    }

    public AiSpeedResult {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(status, "status");
    }

    public boolean ok() {
        return status == Status.OK;
    }
}
