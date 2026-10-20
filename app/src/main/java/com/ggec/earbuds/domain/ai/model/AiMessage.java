package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 对话里的一条消息。不可变：流式过程中每个增量都生成一个新值，界面靠值比较决定要不要重绘。
 */
public record AiMessage(
        String id,
        AiRole role,
        String text,
        @Nullable String reasoning,
        AiMessageStatus status,
        @Nullable AiMessageFailure failure,
        AiFinishReason finishReason,
        @Nullable String modelLabel,
        long createdAtMillis,
        long elapsedMillis,
        @Nullable AiUsage usage) {

    public AiMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(finishReason, "finishReason");
        reasoning = reasoning == null || reasoning.isEmpty() ? null : reasoning;
    }

    public static AiMessage user(String id, String text, long createdAtMillis) {
        return new AiMessage(id, AiRole.USER, text, null, AiMessageStatus.COMPLETE, null,
                AiFinishReason.STOP, null, createdAtMillis, 0L, null);
    }

    public static AiMessage assistantPlaceholder(String id, @Nullable String modelLabel, long createdAtMillis) {
        return new AiMessage(id, AiRole.ASSISTANT, "", null, AiMessageStatus.STREAMING, null,
                AiFinishReason.STOP, modelLabel, createdAtMillis, 0L, null);
    }

    public boolean user() {
        return role == AiRole.USER;
    }

    public boolean streaming() {
        return status == AiMessageStatus.STREAMING;
    }

    public boolean blank() {
        return text.isBlank() && reasoning == null;
    }

    public AiMessage appendText(String delta) {
        return with(text + delta, reasoning, status, failure, finishReason, elapsedMillis, usage);
    }

    public AiMessage appendReasoning(String delta) {
        return with(text, reasoning == null ? delta : reasoning + delta, status, failure, finishReason,
                elapsedMillis, usage);
    }

    public AiMessage withText(String replacement) {
        return with(replacement, reasoning, status, failure, finishReason, elapsedMillis, usage);
    }

    public AiMessage complete(AiFinishReason finish, @Nullable AiUsage tokens, long elapsed) {
        return with(text, reasoning, AiMessageStatus.COMPLETE, null, finish, elapsed, tokens);
    }

    public AiMessage fail(AiFailureKind kind, String detail, long elapsed) {
        return with(text, reasoning, AiMessageStatus.ERROR, new AiMessageFailure(kind, detail),
                finishReason, elapsed, usage);
    }

    public AiMessage stop(long elapsed) {
        return with(text, reasoning, AiMessageStatus.STOPPED, null, finishReason, elapsed, usage);
    }

    private AiMessage with(
            String newText,
            @Nullable String newReasoning,
            AiMessageStatus newStatus,
            @Nullable AiMessageFailure newFailure,
            AiFinishReason newFinish,
            long newElapsed,
            @Nullable AiUsage newUsage) {
        return new AiMessage(id, role, newText, newReasoning, newStatus, newFailure, newFinish, modelLabel,
                createdAtMillis, newElapsed, newUsage);
    }
}
