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

    public YoResponseObject(
            @Nullable String id,
            @Nullable String status,
            @Nullable String model,
            @NonNull List<YoResponseOutputItem> output,
            @Nullable YoResponseUsage usage,
            @NonNull String rawJson) {
        this.id = id;
        this.status = status;
        this.model = model;
        this.output = List.copyOf(output);
        this.usage = usage;
        this.rawJson = rawJson;
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
