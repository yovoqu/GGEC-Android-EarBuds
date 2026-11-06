package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class YoResponseObject {

    @Nullable
    private final String id;
    @Nullable
    private final String status;
    @Nullable
    private final String model;
    private final List<YoResponseOutputItem> output;
    @Nullable
    private final YoResponseUsage usage;
    private final String rawJson;
    @Nullable
    private final Boolean background;
    @Nullable
    private final String incompleteReason;

    public YoResponseObject(
            @Nullable String id,
            @Nullable String status,
            @Nullable String model,
            @NonNull List<YoResponseOutputItem> output,
            @Nullable YoResponseUsage usage,
            @NonNull String rawJson) {
        this(id, status, model, output, usage, rawJson, null, null);
    }

    public YoResponseObject(
            @Nullable String id,
            @Nullable String status,
            @Nullable String model,
            @NonNull List<YoResponseOutputItem> output,
            @Nullable YoResponseUsage usage,
            @NonNull String rawJson,
            @Nullable Boolean background,
            @Nullable String incompleteReason) {
        this.id = id;
        this.status = status;
        this.model = model;
        this.output = List.copyOf(output);
        this.usage = usage;
        this.rawJson = rawJson;
        this.background = background;
        this.incompleteReason = incompleteReason;
    }

    @Nullable
    public String id() {
        return id;
    }

    @Nullable
    public String status() {
        return status;
    }

    @Nullable
    public String model() {
        return model;
    }

    @NonNull
    public List<YoResponseOutputItem> output() {
        return output;
    }

    @Nullable
    public YoResponseUsage usage() {
        return usage;
    }

    /**
     * 后台模式标记；响应没带就是 null。
     */
    @Nullable
    public Boolean background() {
        return background;
    }

    /**
     * 非流式 {@code status=incomplete} 时的 {@code incomplete_details.reason}。
     */
    @Nullable
    public String incompleteReason() {
        return incompleteReason;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }

    @NonNull
    public String text() {
        StringBuilder text = new StringBuilder();
        for (YoResponseOutputItem item : output) {
            if (item.text() != null) {
                text.append(item.text());
            }
        }
        return text.toString();
    }
}
