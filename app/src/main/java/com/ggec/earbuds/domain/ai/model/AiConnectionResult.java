package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 平台最近一次"测试"的结论。{@code fingerprint} 记录测试时的配置（接口地址、协议、API Key 摘要、测试模型），
 * 与当前配置不一致时界面显示"配置已改，需重新测试"。
 */
public record AiConnectionResult(
        boolean ok,
        long firstTokenMillis,
        @Nullable AiFailureKind failureKind,
        @Nullable String message,
        String modelId,
        long testedAtMillis,
        String fingerprint) {

    public AiConnectionResult {
        Objects.requireNonNull(modelId, "modelId");
        fingerprint = fingerprint == null ? "" : fingerprint;
        message = message == null || message.isBlank() ? null : message;
        if (ok) {
            failureKind = null;
        } else if (failureKind == null) {
            failureKind = AiFailureKind.UNKNOWN;
        }
    }

    public static AiConnectionResult success(String modelId, long firstTokenMillis, long testedAtMillis,
                                             String fingerprint) {
        return new AiConnectionResult(true, firstTokenMillis, null, null, modelId, testedAtMillis, fingerprint);
    }

    public static AiConnectionResult failure(String modelId, AiFailureKind kind, @Nullable String message,
                                             long testedAtMillis, String fingerprint) {
        return new AiConnectionResult(false, -1L, kind, message, modelId, testedAtMillis, fingerprint);
    }
}
