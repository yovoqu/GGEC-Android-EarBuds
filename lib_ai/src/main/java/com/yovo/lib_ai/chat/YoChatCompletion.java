package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class YoChatCompletion {

    @Nullable
    private final String id;
    @Nullable
    private final String model;
    private final List<YoChatChoice> choices;
    @Nullable
    private final YoChatUsage usage;
    private final String rawJson;

    public YoChatCompletion(
            @Nullable String id,
            @Nullable String model,
            @NonNull List<YoChatChoice> choices,
            @Nullable YoChatUsage usage,
            @NonNull String rawJson) {
        this.id = id;
        this.model = model;
        this.choices = List.copyOf(choices);
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

    @NonNull
    public List<YoChatChoice> choices() {
        return choices;
    }

    @Nullable
    public YoChatUsage usage() {
        return usage;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }

    @Nullable
    public String text() {
        if (choices.isEmpty() || choices.get(0).message() == null) {
            return null;
        }
        return choices.get(0).message().content();
    }
}
