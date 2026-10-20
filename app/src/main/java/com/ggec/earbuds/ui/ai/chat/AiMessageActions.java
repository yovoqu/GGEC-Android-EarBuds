package com.ggec.earbuds.ui.ai.chat;

import android.view.View;

import androidx.annotation.NonNull;

/**
 * 消息列表里用户能做的全部操作，由对话页实现。列表和消息视图只负责触发，不关心怎么执行。
 */
public interface AiMessageActions {

    void onCopy(@NonNull String text);

    void onCopyCode(@NonNull String code);

    void onRegenerate();

    void onContinue();

    void onDelete(@NonNull String messageId);

    void onToggleReasoning(@NonNull String messageId);

    void onLink(@NonNull String url);

    /** 长按一条消息，{@code anchor} 是弹出菜单的锚点。 */
    void onLongPress(@NonNull View anchor, @NonNull AiMessageUi message);
}
