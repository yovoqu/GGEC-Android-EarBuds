package com.ggec.earbuds.ui.ai.call.subtitle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 格式化多行转写文本解析器 (Transcript Parser)。
 *
 * <p>具备 100% 独立可测试性的无状态纯函数解析器：
 * <ul>
 *   <li>准确识别中文冒号 "你：" / "AI：" 与英文冒号 "User:" / "AI:"；</li>
 *   <li>长回复多行段落自动保留在同一个发言回合内，绝不发生误分割；</li>
 *   <li>自动处理末尾流式未完成标识 "…"。</li>
 * </ul>
 * </p>
 */
public final class AiSubtitleTranscriptParser {

    private AiSubtitleTranscriptParser() {}

    @NonNull
    public static List<AiSubtitleItem> parse(@Nullable String rawTranscript) {
        if (rawTranscript == null || rawTranscript.isBlank()) {
            return List.of();
        }
        List<AiSubtitleItem> result = new ArrayList<>();
        String[] lines = rawTranscript.split("\n");
        Boolean currentUser = null;
        StringBuilder currentContent = new StringBuilder();
        int turnIndex = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("你：") || trimmed.startsWith("你:")
                    || trimmed.startsWith("User：") || trimmed.startsWith("User:")) {
                if (currentUser != null && currentContent.length() > 0) {
                    turnIndex++;
                    result.add(createItem("turn-" + turnIndex, currentUser, currentContent.toString()));
                    currentContent.setLength(0);
                }
                currentUser = true;
                int idx = trimmed.indexOf('：');
                if (idx < 0) {
                    idx = trimmed.indexOf(':');
                }
                currentContent.append(trimmed.substring(idx + 1).trim());
            } else if (trimmed.startsWith("AI：") || trimmed.startsWith("AI:") || trimmed.startsWith("AI ")) {
                if (currentUser != null && currentContent.length() > 0) {
                    turnIndex++;
                    result.add(createItem("turn-" + turnIndex, currentUser, currentContent.toString()));
                    currentContent.setLength(0);
                }
                currentUser = false;
                int idx = trimmed.indexOf('：');
                if (idx < 0) {
                    idx = trimmed.indexOf(':');
                }
                if (idx < 0) {
                    idx = trimmed.indexOf(' ');
                }
                currentContent.append(trimmed.substring(idx + 1).trim());
            } else {
                if (currentUser == null) {
                    currentUser = false;
                }
                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                currentContent.append(trimmed);
            }
        }
        if (currentUser != null && currentContent.length() > 0) {
            turnIndex++;
            result.add(createItem("turn-" + turnIndex, currentUser, currentContent.toString()));
        }
        return List.copyOf(result);
    }

    private static AiSubtitleItem createItem(String id, boolean isUser, String content) {
        boolean isFinal = !content.endsWith("…");
        String clean = isFinal ? content : content.substring(0, content.length() - 1);
        return new AiSubtitleItem(
                id,
                isUser ? AiSubtitleItem.Speaker.USER : AiSubtitleItem.Speaker.AI,
                clean,
                isFinal
        );
    }
}
