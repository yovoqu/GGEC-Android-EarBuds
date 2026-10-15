package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Chat Completions 的 tool_choice。函数形式编码为
 * {@code {"type":"function","function":{"name":"..."}}}。
 */
public final class YoChatToolChoice {

    public enum Kind {
        AUTO,
        NONE,
        REQUIRED,
        FUNCTION
    }

    private final Kind kind;
    @Nullable
    private final String functionName;

    private YoChatToolChoice(Kind kind, @Nullable String functionName) {
        this.kind = kind;
        this.functionName = functionName;
    }

    @NonNull
    public static YoChatToolChoice auto() {
        return new YoChatToolChoice(Kind.AUTO, null);
    }

    @NonNull
    public static YoChatToolChoice none() {
        return new YoChatToolChoice(Kind.NONE, null);
    }

    @NonNull
    public static YoChatToolChoice required() {
        return new YoChatToolChoice(Kind.REQUIRED, null);
    }

    @NonNull
    public static YoChatToolChoice function(@NonNull String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("function name is required");
        }
        return new YoChatToolChoice(Kind.FUNCTION, name);
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
