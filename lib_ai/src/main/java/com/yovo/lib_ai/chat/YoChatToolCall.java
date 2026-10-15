package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class YoChatToolCall {

    private final int index;
    private final String id;
    private final String name;
    private final String arguments;

    public YoChatToolCall(@NonNull String id, @NonNull String name, @NonNull String arguments) {
        this(-1, id, name, arguments);
    }

    public YoChatToolCall(int index, @NonNull String id, @NonNull String name, @NonNull String arguments) {
        this.index = index;
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public int index() {
        return index;
    }

    @NonNull
    public String id() {
        return id;
    }

    @NonNull
    public String name() {
        return name;
    }

    @NonNull
    public String arguments() {
        return arguments;
    }

    @Nullable
    public static YoChatToolCall orNull(@Nullable String id, @Nullable String name, @Nullable String arguments) {
        if (id == null || name == null) {
            return null;
        }
        return new YoChatToolCall(id, name, arguments == null ? "" : arguments);
    }
}
