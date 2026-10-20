package com.ggec.earbuds.ui.ai.chat;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;

/**
 * 一条消息在界面上需要的全部信息，已经把"能不能重新生成"这类判断做完。
 * 值相等就代表画面相同，列表据此只刷新变化的那一条。
 */
public record AiMessageUi(
        String id,
        boolean fromUser,
        String text,
        @Nullable String reasoning,
        boolean reasoningExpanded,
        boolean streaming,
        Notice notice,
        @Nullable AiFailureKind failureKind,
        String failureDetail,
        String modelLabel,
        long elapsedMillis,
        int outputTokens,
        boolean canRegenerate,
        boolean canContinue,
        boolean canDelete) {

    /** 消息下方需要额外说明的情况。 */
    public enum Notice {
        NONE,
        STOPPED,
        TRUNCATED,
        FILTERED,
        EMPTY,
        ERROR
    }

    public boolean waitingForFirstToken() {
        return streaming && text.isEmpty() && reasoning == null;
    }
}
