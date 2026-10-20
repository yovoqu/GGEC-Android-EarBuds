package com.ggec.earbuds.domain.ai.chat;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiConversation;

import java.util.Objects;

/**
 * 对话页某一刻的完整状态。不可变，会话每次变化都发布一个新值。
 * {@code loaded} 为假时历史还没读完，界面先不要判断"空对话"或"未配置"。
 * {@code historyRevision} 在历史列表的内容发生变化（保存、删除、改名、清空）时递增，
 * 打开着历史面板的界面据此重新读取列表。
 */
public record AiChatState(
        boolean loaded,
        AiConversation conversation,
        boolean generating,
        Readiness readiness,
        @Nullable AiActiveModel model,
        AiChatPreferences preferences,
        boolean storageFailed,
        int historyRevision) {

    public enum Readiness {
        /** 没有任何已配置密钥的平台。 */
        NO_PLATFORM,
        /** 有平台，但没有选中可用的模型。 */
        NO_MODEL,
        READY
    }

    public AiChatState {
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(readiness, "readiness");
        Objects.requireNonNull(preferences, "preferences");
    }

    public static AiChatState initial(AiConversation conversation) {
        return new AiChatState(false, conversation, false, Readiness.NO_PLATFORM, null,
                AiChatPreferences.DEFAULT, false, 0);
    }

    public boolean ready() {
        return readiness == Readiness.READY;
    }
}
