package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Chat Completions 的一条消息。内容可以是字符串，也可以是内容片段。
 */
public final class YoChatMessage {

    private final String role;
    @Nullable
    private final String content;
    @Nullable
    private final YoChatContentPart[] parts;
    private final YoChatToolCall[] toolCalls;
    @Nullable
    private final String toolCallId;
    @Nullable
    private final String refusal;

    private YoChatMessage(
            String role,
            @Nullable String content,
            @Nullable YoChatContentPart[] parts,
            YoChatToolCall[] toolCalls,
            @Nullable String toolCallId,
            @Nullable String refusal) {
        this.role = role;
        this.content = content;
        this.parts = parts;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.refusal = refusal;
    }

    @NonNull
    public static YoChatMessage of(@NonNull String role, @NonNull String content) {
        return builder().role(role).content(content).build();
    }

    @NonNull
    public static YoChatMessage system(@NonNull String content) {
        return of("system", content);
    }

    @NonNull
    public static YoChatMessage user(@NonNull String content) {
        return of("user", content);
    }

    @NonNull
    public static YoChatMessage assistant(@NonNull String content) {
        return of("assistant", content);
    }

    @NonNull
    public static YoChatMessage tool(@NonNull String toolCallId, @NonNull String content) {
        return builder().role("tool").toolCallId(toolCallId).content(content).build();
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public String role() {
        return role;
    }

    @Nullable
    public String content() {
        return content;
    }

    @Nullable
    public YoChatContentPart[] parts() {
        return parts == null ? null : parts.clone();
    }

    @NonNull
    public YoChatToolCall[] toolCalls() {
        return toolCalls.clone();
    }

    @Nullable
    public String toolCallId() {
        return toolCallId;
    }

    @Nullable
    public String refusal() {
        return refusal;
    }

    public static final class Builder {
        private String role;
        private String content;
        private YoChatContentPart[] parts;
        private YoChatToolCall[] toolCalls = new YoChatToolCall[0];
        private String toolCallId;
        private String refusal;

        @NonNull
        public Builder role(@NonNull String role) {
            if (role.isBlank()) {
                throw new IllegalArgumentException("role is required");
            }
            this.role = role;
            return this;
        }

        @NonNull
        public Builder content(@Nullable String content) {
            this.content = content;
            return this;
        }

        @NonNull
        public Builder parts(@NonNull YoChatContentPart... parts) {
            this.parts = parts.clone();
            return this;
        }

        @NonNull
        public Builder toolCalls(@NonNull YoChatToolCall... toolCalls) {
            this.toolCalls = toolCalls.clone();
            return this;
        }

        @NonNull
        public Builder toolCallId(@NonNull String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        @NonNull
        public Builder refusal(@NonNull String refusal) {
            this.refusal = refusal;
            return this;
        }

        @NonNull
        public YoChatMessage build() {
            if (role == null) {
                throw new IllegalArgumentException("role is required");
            }
            if (content == null && (parts == null || parts.length == 0) && toolCalls.length == 0 && refusal == null) {
                throw new IllegalArgumentException("message content is required");
            }
            return new YoChatMessage(role, content, parts, toolCalls, toolCallId, refusal);
        }
    }
}
