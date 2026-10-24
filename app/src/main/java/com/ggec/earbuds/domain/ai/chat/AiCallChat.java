package com.ggec.earbuds.domain.ai.chat;

import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiUsageSource;
import com.ggec.earbuds.domain.ai.port.AiChatGateway;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;
import com.ggec.earbuds.domain.ai.port.AiIdGenerator;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.usage.AiUsageStore;
import com.ggec.earbuds.domain.ai.usecase.RecordUsage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 通话专用的对话会话（CALL-10，Q8）：通话内容只留在内存里，不进对话历史；用量照记，来源"通话"。
 *
 * <p>每次通话新建一个，挂断后连同内容一起丢掉。上下文、停止、重试与对话页走同一套 {@link AiChatSession}。</p>
 */
public final class AiCallChat {

    private AiCallChat() {
    }

    public static AiChatSession create(AiSettingsRepository settings, AiChatGateway gateway, AiClock clock,
                                       AiIdGenerator ids, AiUsageStore usage, Executor executor) {
        return new AiChatSession(settings, new MemoryConversations(), gateway, clock, ids,
                new RecordUsage(usage, clock, AiUsageSource.CALL), executor);
    }

    /** 只活在这次通话里的对话存储。 */
    static final class MemoryConversations implements AiConversationRepository {

        private final Map<String, AiConversation> stored = new LinkedHashMap<>();

        @Override
        public synchronized List<AiConversationSummary> summaries() {
            List<AiConversationSummary> summaries = new ArrayList<>();
            for (AiConversation conversation : stored.values()) {
                summaries.add(AiConversationSummary.of(conversation));
            }
            summaries.sort(Comparator.comparingLong(AiConversationSummary::updatedAtMillis).reversed());
            return summaries;
        }

        @Override
        public synchronized Optional<AiConversation> find(String id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public synchronized void save(AiConversation conversation) {
            stored.put(conversation.id(), conversation);
        }

        @Override
        public synchronized void delete(String id) {
            stored.remove(id);
        }

        @Override
        public synchronized void deleteAll() {
            stored.clear();
        }
    }
}
