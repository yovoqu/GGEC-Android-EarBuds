package com.yovo.lib_ai.completion;

import androidx.annotation.NonNull;

/**
 * 对话里的一轮发言。系统提示词不是一轮发言，放在请求的 {@code system} 上。
 */
public final class YoCompletionTurn {

    public enum Role {
        USER,
        ASSISTANT
    }

    private final Role role;
    private final String text;

    private YoCompletionTurn(Role role, String text) {
        this.role = role;
        this.text = text;
    }

    @NonNull
    public static YoCompletionTurn user(@NonNull String text) {
        return of(Role.USER, text);
    }

    @NonNull
    public static YoCompletionTurn assistant(@NonNull String text) {
        return of(Role.ASSISTANT, text);
    }

    @NonNull
    public static YoCompletionTurn of(@NonNull Role role, @NonNull String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("turn text is required");
        }
        return new YoCompletionTurn(role, text);
    }

    @NonNull
    public Role role() {
        return role;
    }

    @NonNull
    public String text() {
        return text;
    }
}
