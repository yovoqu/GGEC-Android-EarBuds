package com.ggec.earbuds.domain.ai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 一段对话。标题为空表示还没有命名，界面显示"新对话"。空对话在发出第一条消息前不落盘。
 */
public record AiConversation(
        String id,
        String title,
        long createdAtMillis,
        long updatedAtMillis,
        List<AiMessage> messages) {

    public AiConversation {
        Objects.requireNonNull(id, "id");
        title = title == null ? "" : title;
        messages = List.copyOf(messages);
    }

    public static AiConversation empty(String id, long nowMillis) {
        return new AiConversation(id, "", nowMillis, nowMillis, List.of());
    }

    public boolean empty() {
        return messages.isEmpty();
    }

    public boolean untitled() {
        return title.isBlank();
    }

    public Optional<AiMessage> last() {
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(messages.size() - 1));
    }

    public AiConversation withMessages(List<AiMessage> next, long updatedAt) {
        return new AiConversation(id, title, createdAtMillis, updatedAt, next);
    }

    public AiConversation withTitle(String next) {
        return new AiConversation(id, next, createdAtMillis, updatedAtMillis, messages);
    }

    public AiConversation withMessageReplaced(AiMessage replacement) {
        List<AiMessage> next = new ArrayList<>(messages);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(replacement.id())) {
                next.set(i, replacement);
                break;
            }
        }
        return new AiConversation(id, title, createdAtMillis, updatedAtMillis, next);
    }
}
