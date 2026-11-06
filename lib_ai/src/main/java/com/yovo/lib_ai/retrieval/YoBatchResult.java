package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 批量结果里的一行，按 {@code custom_id} 与输入对齐。
 *
 * <p>成功时 {@link #statusCode()} 与 {@link #body()} 有值；失败时 {@link #error()} 说明原因。</p>
 */
public final class YoBatchResult {

    private final String customId;
    @Nullable
    private final Integer statusCode;
    @Nullable
    private final String body;
    @Nullable
    private final String error;

    public YoBatchResult(
            @NonNull String customId,
            @Nullable Integer statusCode,
            @Nullable String body,
            @Nullable String error) {
        if (customId == null || customId.isBlank()) {
            throw new IllegalArgumentException("customId is required");
        }
        this.customId = customId;
        this.statusCode = statusCode;
        this.body = body;
        this.error = error;
    }

    @NonNull
    public String customId() {
        return customId;
    }

    @Nullable
    public Integer statusCode() {
        return statusCode;
    }

    @Nullable
    public String body() {
        return body;
    }

    @Nullable
    public String error() {
        return error;
    }

    public boolean succeeded() {
        return error == null && statusCode != null && statusCode >= 200 && statusCode < 300;
    }
}
