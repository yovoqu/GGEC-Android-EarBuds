package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class YoMessageTurn {

    private final String role;
    @Nullable
    private final String text;
    private final List<YoMessageContent> blocks;

    private YoMessageTurn(String role, @Nullable String text, List<YoMessageContent> blocks) {
        this.role = role;
        this.text = text;
        this.blocks = blocks;
    }

    @NonNull
    public static YoMessageTurn user(@NonNull String text) {
        return of("user", text);
    }

    @NonNull
    public static YoMessageTurn assistant(@NonNull String text) {
        return of("assistant", text);
    }

    @NonNull
    public static YoMessageTurn of(@NonNull String role, @NonNull String text) {
        validateRole(role);
        return new YoMessageTurn(role, text, List.of());
    }

    @NonNull
    public static YoMessageTurn of(@NonNull String role, @NonNull List<YoMessageContent> blocks) {
        validateRole(role);
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("content blocks are required");
        }
        return new YoMessageTurn(role, null, List.copyOf(blocks));
    }

    @NonNull
    public String role() {
        return role;
    }

    @Nullable
    public String text() {
        return text;
    }

    @NonNull
    public List<YoMessageContent> blocks() {
        return blocks;
    }

    private static void validateRole(String role) {
        if (!"user".equals(role) && !"assistant".equals(role)) {
            throw new IllegalArgumentException("Anthropic message role must be user or assistant");
        }
    }
}
