package com.ggec.earbuds.domain.ai.policy;

import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 进程在生成途中被杀掉时，磁盘上会留下状态仍为"生成中"的助手消息。重新打开对话时把它收尾：
 * 有内容的标为已停止，什么都没收到的直接丢掉。
 */
public final class AiMessageRecovery {

    public AiConversation recover(AiConversation conversation) {
        boolean changed = false;
        List<AiMessage> next = new ArrayList<>();
        for (AiMessage message : conversation.messages()) {
            if (!message.streaming()) {
                next.add(message);
                continue;
            }
            changed = true;
            if (!message.blank()) {
                next.add(message.stop(message.elapsedMillis()));
            }
        }
        return changed ? conversation.withMessages(next, conversation.updatedAtMillis()) : conversation;
    }
}
