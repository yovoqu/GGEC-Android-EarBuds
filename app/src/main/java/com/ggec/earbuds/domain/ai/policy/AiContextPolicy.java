package com.ggec.earbuds.domain.ai.policy;

import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiMessageStatus;
import com.ggec.earbuds.domain.ai.model.AiRole;
import com.ggec.earbuds.domain.ai.port.AiChatTurn;

import java.util.ArrayList;
import java.util.List;

/**
 * 决定一次请求带哪些历史（CHAT-14）：只带正常说完且有内容的消息；失败、停止、未完成和仍在生成的不带；
 * 超出条数上限时丢最早的，并保证从用户发言开始，最后一条是用户发言。
 */
public final class AiContextPolicy {

    public List<AiChatTurn> window(List<AiMessage> history, int maxMessages) {
        List<AiChatTurn> turns = new ArrayList<>();
        for (AiMessage message : history) {
            if (!usable(message)) {
                continue;
            }
            turns.add(new AiChatTurn(message.role(), message.text()));
        }
        if (maxMessages > 0 && turns.size() > maxMessages) {
            turns = new ArrayList<>(turns.subList(turns.size() - maxMessages, turns.size()));
        }
        while (!turns.isEmpty() && turns.get(0).role() != AiRole.USER) {
            turns.remove(0);
        }
        if (turns.isEmpty() || turns.get(turns.size() - 1).role() != AiRole.USER) {
            throw new IllegalStateException("A request must end with a user message");
        }
        return turns;
    }

    private static boolean usable(AiMessage message) {
        if (message.text().isBlank()) {
            return false;
        }
        return message.status() == AiMessageStatus.COMPLETE;
    }
}
