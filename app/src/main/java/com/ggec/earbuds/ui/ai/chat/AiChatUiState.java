package com.ggec.earbuds.ui.ai.chat;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 对话页的整体界面状态。
 * {@code subtitle} 为空表示还没有可用的模型；{@code welcome} 不是 NONE 时显示欢迎或引导，而不是消息列表。
 */
public record AiChatUiState(
        boolean loaded,
        String subtitle,
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
     * @param modelChip 输入区里的模型标签文字；为空表示还没有可用模型，标签显示"选择模型"
     */
    public record Composer(boolean enabled, boolean generating, @Nullable String modelChip) {
    }

    public AiChatUiState {
        messages = List.copyOf(messages);
    }

    public static AiChatUiState empty() {
        return new AiChatUiState(false, "", List.of(), Welcome.NONE, new Composer(false, false, null), false);
    }
}
