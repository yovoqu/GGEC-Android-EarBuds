package com.ggec.earbuds.ui.ai.chat;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 对话页的整体界面状态。
 * {@code welcome} 不是 NONE 时显示空状态，而不是消息列表；当前模型只在 {@code composer} 里出现一次。
 */
public record AiChatUiState(
        boolean loaded,
        List<AiMessageUi> messages,
        Welcome welcome,
        Composer composer,
        boolean storageFailed) {

    public enum Welcome {
        NONE,
        SUGGESTIONS,
        NEEDS_PLATFORM,
        NEEDS_MODEL
    }

    /**
     * @param modelChip 输入区上方的模型文字；为空表示还没有可用模型，那一行不显示
     */
    public record Composer(boolean enabled, boolean generating, @Nullable String modelChip) {
    }

    public AiChatUiState {
        messages = List.copyOf(messages);
    }

    public static AiChatUiState empty() {
        return new AiChatUiState(false, List.of(), Welcome.NONE, new Composer(false, false, null), false);
    }
}
