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

    private static final int PREVIEW_CODE_POINTS = 60;

    public AiConversationSummary {
        Objects.requireNonNull(id, "id");
        title = title == null ? "" : title;
        preview = preview == null ? "" : preview;
    }

    public static AiConversationSummary of(AiConversation conversation) {
        String preview = "";
        for (int i = conversation.messages().size() - 1; i >= 0; i--) {
            // 摘要只有一行位置：先把 Markdown 记号去掉，再截断。
            // 直接截原文会出现「## 标题 **加粗…」这种半截记号。
            String text = AiPlainText.of(conversation.messages().get(i).text());
            if (!text.isEmpty()) {
                preview = AiPlainText.truncate(text, PREVIEW_CODE_POINTS);
                break;
            }
        }
        return new AiConversationSummary(conversation.id(), conversation.title(),
                conversation.updatedAtMillis(), conversation.messages().size(), preview);
    }
}
