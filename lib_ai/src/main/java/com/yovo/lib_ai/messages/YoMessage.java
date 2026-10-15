package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class YoMessage {

    @Nullable
    private final String id;
    @Nullable
    private final String model;
    @Nullable
    private final String stopReason;
    private final List<YoMessageContent> content;
    @Nullable
    private final YoMessageUsage usage;
    private final String rawJson;

    public YoMessage(
            @Nullable String id,
            @Nullable String model,
            @Nullable String stopReason,
            @NonNull List<YoMessageContent> content,
            @Nullable YoMessageUsage usage,
            @NonNull String rawJson) {
        this.id = id;
        this.model = model;
        this.stopReason = stopReason;
        this.content = List.copyOf(content);
        this.usage = usage;
        this.rawJson = rawJson;
    }

    @Nullable
    public String id() {
        return id;
    }

    @Nullable
    public String model() {
        return model;
    }

    @Nullable
    public String stopReason() {
        return stopReason;
    }

    @NonNull
    public List<YoMessageContent> content() {
        return content;
    }

    @Nullable
    public YoMessageUsage usage() {
        return usage;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }

    @NonNull
    public String text() {
        StringBuilder text = new StringBuilder();
        for (YoMessageContent block : content) {
            if (block.type() == YoMessageContent.Type.TEXT && block.text() != null) {
                text.append(block.text());
            }
        }
        return text.toString();
    }
}
