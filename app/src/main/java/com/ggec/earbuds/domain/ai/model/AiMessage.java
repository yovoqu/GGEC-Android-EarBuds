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
        @Nullable AiUsage usage,
        long firstTokenMillis,
        long reasoningMillis,
        @Nullable String platformId,
        @Nullable String modelId) {

    /** 兼容旧调用：没有首字、思考时长和来源。 */
    public AiMessage(String id, AiRole role, String text, @Nullable String reasoning, AiMessageStatus status,
                     @Nullable AiMessageFailure failure, AiFinishReason finishReason, @Nullable String modelLabel,
                     long createdAtMillis, long elapsedMillis, @Nullable AiUsage usage) {
        this(id, role, text, reasoning, status, failure, finishReason, modelLabel, createdAtMillis, elapsedMillis,
                usage, -1L, -1L, null, null);
    }

    public AiMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(finishReason, "finishReason");
        reasoning = reasoning == null || reasoning.isEmpty() ? null : reasoning;
        firstTokenMillis = firstTokenMillis < 0 ? -1L : firstTokenMillis;
        reasoningMillis = reasoningMillis < 0 ? -1L : reasoningMillis;
    }

    public boolean hasFirstToken() {
        return firstTokenMillis >= 0;
    }

    /** 记下首字（发出请求到第一个输出片段的毫秒数）。已有值时不覆盖。 */
    public AiMessage withFirstToken(long millis) {
        if (hasFirstToken()) {
            return this;
        }
        return new AiMessage(id, role, text, reasoning, status, failure, finishReason, modelLabel, createdAtMillis,
                elapsedMillis, usage, millis, reasoningMillis, platformId, modelId);
    }

    public AiMessage withReasoningMillis(long millis) {
        return new AiMessage(id, role, text, reasoning, status, failure, finishReason, modelLabel, createdAtMillis,
                elapsedMillis, usage, firstTokenMillis, millis, platformId, modelId);
    }

    /** 回答来自哪个平台的哪个模型（"去设置"据此打开平台页）。 */
    public AiMessage withOrigin(@Nullable String platform, @Nullable String model) {
        return new AiMessage(id, role, text, reasoning, status, failure, finishReason, modelLabel, createdAtMillis,
                elapsedMillis, usage, firstTokenMillis, reasoningMillis, platform, model);
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

    /** 上次生成没收尾（进程被杀）：保留已收到的内容，标为"未完成"。 */
    public AiMessage interrupt() {
        return with(text, reasoning, AiMessageStatus.INTERRUPTED, null, finishReason, elapsedMillis, usage);
    }

    /** 思考过程已有内容但还没记下时长时，用"到 {@code sinceStart} 为止"补上。 */
    public AiMessage closeReasoning(long sinceStart) {
        if (reasoning == null || reasoningMillis >= 0 || !hasFirstToken()) {
            return this;
        }
        return withReasoningMillis(Math.max(0L, sinceStart - firstTokenMillis));
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
                createdAtMillis, newElapsed, newUsage, firstTokenMillis, reasoningMillis, platformId, modelId);
    }
}
