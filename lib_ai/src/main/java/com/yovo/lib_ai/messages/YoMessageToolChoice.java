package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class YoMessageToolChoice {

    public enum Kind {
        AUTO,
        ANY,
        NONE,
        TOOL
    }

    private final Kind kind;
    @Nullable
    private final String name;

    private YoMessageToolChoice(Kind kind, @Nullable String name) {
        this.kind = kind;
        this.name = name;
    }

    @NonNull
    public static YoMessageToolChoice auto() {
        return new YoMessageToolChoice(Kind.AUTO, null);
    }

    @NonNull
    public static YoMessageToolChoice any() {
        return new YoMessageToolChoice(Kind.ANY, null);
    }

    @NonNull
    public static YoMessageToolChoice none() {
        return new YoMessageToolChoice(Kind.NONE, null);
    }

    @NonNull
    public static YoMessageToolChoice tool(@NonNull String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("tool name is required");
        }
        return new YoMessageToolChoice(Kind.TOOL, name);
    }

    @NonNull
    public Kind kind() {
        return kind;
    }

    @Nullable
    public String name() {
        return name;
    }
}
