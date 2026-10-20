package com.ggec.earbuds.domain.ai.model;

import java.util.Objects;

/**
 * 历史列表里的一行：不带完整消息，打开时再按 id 读取。
 */
public record AiConversationSummary(
        String id,
        String title,
        long updatedAtMillis,
        int messageCount,
        String preview) {

    private static final int PREVIEW_LENGTH = 80;

    public AiConversationSummary {
        Objects.requireNonNull(id, "id");
        title = title == null ? "" : title;
        preview = preview == null ? "" : preview;
    }

    public static AiConversationSummary of(AiConversation conversation) {
        String preview = "";
        for (int i = conversation.messages().size() - 1; i >= 0; i--) {
            String text = conversation.messages().get(i).text().replaceAll("\\s+", " ").strip();
            if (!text.isEmpty()) {
                preview = text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) : text;
                break;
            }
        }
        return new AiConversationSummary(conversation.id(), conversation.title(),
                conversation.updatedAtMillis(), conversation.messages().size(), preview);
    }
}
