package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Chat Completions 的一条消息。内容可以是字符串，也可以是内容片段。
 *
 * <p>思考内容（{@code reasoning_content} / {@code reasoning}）是独立字段，不能塞进
 * {@code content}：DeepSeek、Qwen3.8、GLM 等模型把它单独返回，混进正文会污染回复。
 * 同样，{@code name} 和 {@code audio.id} 是协议字段，保留原样以便多轮音频回放。</p>
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
    @Nullable
    private final String reasoningContent;
    @Nullable
    private final String name;
    @Nullable
    private final String audioId;
    private final boolean prefix;

    private YoChatMessage(
            String role,
            @Nullable String content,
            @Nullable YoChatContentPart[] parts,
            YoChatToolCall[] toolCalls,
            @Nullable String toolCallId,
            @Nullable String refusal,
            @Nullable String reasoningContent,
            @Nullable String name,
            @Nullable String audioId,
            boolean prefix) {
        this.role = role;
        this.content = content;
        this.parts = parts;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.refusal = refusal;
        this.reasoningContent = reasoningContent;
        this.name = name;
        this.audioId = audioId;
        this.prefix = prefix;
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

    /** 模型返回的思考内容（{@code reasoning_content} / {@code reasoning}）。没有则为 null。 */
    @Nullable
    public String reasoningContent() {
        return reasoningContent;
    }

    /** 参与方名字（多说话人场景）。 */
    @Nullable
    public String name() {
        return name;
    }

    /** 上一轮音频响应的 id，用于把音频作为上下文回传。 */
    @Nullable
    public String audioId() {
        return audioId;
    }

    /** DeepSeek 的聊天前缀续写标记；只有该方言会发送。 */
    public boolean prefix() {
        return prefix;
    }

    public static final class Builder {
        private String role;
        private String content;
        private YoChatContentPart[] parts;
        private YoChatToolCall[] toolCalls = new YoChatToolCall[0];
        private String toolCallId;
        private String refusal;
        private String reasoningContent;
        private String name;
        private String audioId;
        private boolean prefix;

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
        public Builder reasoningContent(@Nullable String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

        @NonNull
        public Builder name(@Nullable String name) {
            this.name = name;
            return this;
        }

        @NonNull
        public Builder audioId(@Nullable String audioId) {
            this.audioId = audioId;
            return this;
        }

        @NonNull
        public Builder prefix(boolean prefix) {
            this.prefix = prefix;
            return this;
        }

        @NonNull
        public YoChatMessage build() {
            if (role == null) {
                throw new IllegalArgumentException("role is required");
            }
            if (content == null
                    && (parts == null || parts.length == 0)
                    && toolCalls.length == 0
                    && refusal == null
                    && reasoningContent == null) {
                throw new IllegalArgumentException("message content is required");
            }
            return new YoChatMessage(
                    role, content, parts, toolCalls, toolCallId, refusal,
                    reasoningContent, name, audioId, prefix);
        }
    }
}
