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
        long testedAtMillis,
        /** 平台回报的输出 token 数；未回报为 -1。用来算 tok/s 而不是拿总时长充数。 */
        int outputTokens) {

    public enum Status {
        OK,
        FAILED,
        TIMEOUT
    }

    /** 旧调用方（测试与历史数据）不关心 token 数。 */
    public AiSpeedResult(String modelId, Status status, long firstTokenMillis, long totalMillis,
                         @Nullable AiFailureKind failureKind, @Nullable String message,
                         long testedAtMillis) {
        this(modelId, status, firstTokenMillis, totalMillis, failureKind, message, testedAtMillis, -1);
    }

    public AiSpeedResult {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(status, "status");
    }

    public boolean ok() {
        return status == Status.OK;
    }

    /**
     * 生成阶段的吞吐（tok/s）。算不出来时返回 -1：
     * 没有 usage、或首字时间就是总时长（只有一段输出）时，除数为零或没有意义。
     */
    public int tokensPerSecond() {
        if (!ok() || outputTokens <= 0 || totalMillis <= firstTokenMillis) {
            return -1;
        }
        long generating = totalMillis - firstTokenMillis;
        return (int) Math.round(outputTokens * 1000d / generating);
    }
}
