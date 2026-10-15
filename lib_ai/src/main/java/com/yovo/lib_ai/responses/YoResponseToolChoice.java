package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Responses API 的 tool_choice。函数形式是扁平的
 * {@code {"type":"function","name":"..."}}，和 Chat Completions 不同。
 */
public final class YoResponseToolChoice {

    public enum Kind {
        AUTO,
        NONE,
        REQUIRED,
        FUNCTION
    }

    private final Kind kind;
    @Nullable
    private final String functionName;

    private YoResponseToolChoice(Kind kind, @Nullable String functionName) {
        this.kind = kind;
        this.functionName = functionName;
    }

    @NonNull
    public static YoResponseToolChoice auto() {
        return new YoResponseToolChoice(Kind.AUTO, null);
    }

    @NonNull
    public static YoResponseToolChoice none() {
        return new YoResponseToolChoice(Kind.NONE, null);
    }

    @NonNull
    public static YoResponseToolChoice required() {
        return new YoResponseToolChoice(Kind.REQUIRED, null);
    }

    @NonNull
    public static YoResponseToolChoice function(@NonNull String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("function name is required");
        }
        return new YoResponseToolChoice(Kind.FUNCTION, name);
    }

    @NonNull
    public Kind kind() {
        return kind;
    }

    @Nullable
    public String functionName() {
        return functionName;
    }
}
