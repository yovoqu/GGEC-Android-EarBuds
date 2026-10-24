package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiPlainText;

import java.util.Locale;

/**
 * 历史搜索（HIS-04）：标题和消息正文，不区分大小写。正文命中时给出命中处前后约 20 字的片段。
 */
public final class AiHistorySearch {

    static final int CONTEXT_CHARS = 20;

    /**
     * @param snippet    命中片段（只命中标题时为空，界面显示普通摘要）
     * @param hitStart   片段里命中词的起点，-1 表示没有
     */
    public record Match(boolean titleHit, @Nullable String snippet, int hitStart, int hitLength) {
    }

    private AiHistorySearch() {
    }

    @Nullable
    public static Match match(AiConversation conversation, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty()) {
            return null;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        boolean titleHit = conversation.title().toLowerCase(Locale.ROOT).contains(needle);
        for (int i = conversation.messages().size() - 1; i >= 0; i--) {
            AiMessage message = conversation.messages().get(i);
            String plain = AiPlainText.of(message.text()).replace('\n', ' ');
            int at = plain.toLowerCase(Locale.ROOT).indexOf(needle);
            if (at >= 0) {
                int start = Math.max(0, at - CONTEXT_CHARS);
                int end = Math.min(plain.length(), at + needle.length() + CONTEXT_CHARS);
                String prefix = start > 0 ? "…" : "";
                String suffix = end < plain.length() ? "…" : "";
                String snippet = prefix + plain.substring(start, end) + suffix;
                return new Match(titleHit, snippet, prefix.length() + (at - start), needle.length());
            }
        }
        return titleHit ? new Match(true, null, -1, 0) : null;
    }
}
