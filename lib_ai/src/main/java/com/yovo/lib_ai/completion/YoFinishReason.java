package com.yovo.lib_ai.completion;

import androidx.annotation.Nullable;

/**
 * 三条协议的结束原因归一后的取值。
 */
public enum YoFinishReason {
    /** 自然结束或命中停止序列。 */
    STOP,
    /** 达到输出长度上限，回答被截断。 */
    LENGTH,
    /** 被内容安全策略拦截。 */
    FILTERED,
    OTHER;

    /**
     * Chat Completions 的 {@code finish_reason}。缺失按自然结束处理，部分兼容主机不返回该字段。
     */
    public static YoFinishReason fromChat(@Nullable String reason) {
        if (reason == null) {
            return STOP;
        }
        return switch (reason) {
            case "stop" -> STOP;
            case "length" -> LENGTH;
            case "content_filter" -> FILTERED;
            default -> OTHER;
        };
    }

    /**
     * Anthropic Messages 的 {@code stop_reason}。
     */
    public static YoFinishReason fromMessages(@Nullable String reason) {
        if (reason == null) {
            return STOP;
        }
        return switch (reason) {
            case "end_turn", "stop_sequence", "pause_turn" -> STOP;
            case "max_tokens", "model_context_window_exceeded" -> LENGTH;
            case "refusal" -> FILTERED;
            default -> OTHER;
        };
    }

    /**
     * Responses 的 {@code status} 与 {@code incomplete_details.reason}。
     */
    public static YoFinishReason fromResponses(@Nullable String status, @Nullable String incompleteReason) {
        if (status == null || "completed".equals(status)) {
            return STOP;
        }
        if ("incomplete".equals(status)) {
            if ("max_output_tokens".equals(incompleteReason)) {
                return LENGTH;
            }
            if ("content_filter".equals(incompleteReason)) {
                return FILTERED;
            }
        }
        return OTHER;
    }
}
