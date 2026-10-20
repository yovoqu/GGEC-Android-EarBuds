package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MemoryConversationRepository implements AiConversationRepository {

    public final Map<String, AiConversation> stored = new LinkedHashMap<>();
    public boolean failSaves;
    public int saves;

    @Override
    public List<AiConversationSummary> summaries() {
        List<AiConversationSummary> summaries = new ArrayList<>();
        for (AiConversation conversation : stored.values()) {
            summaries.add(AiConversationSummary.of(conversation));
        }
        summaries.sort(Comparator.comparingLong(AiConversationSummary::updatedAtMillis).reversed());
        return summaries;
    }

    @Override
    public Optional<AiConversation> find(String id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public void save(AiConversation conversation) {
        if (failSaves) {
            throw new IllegalStateException("disk is full");
        }
        saves++;
        stored.put(conversation.id(), conversation);
    }

    @Override
    public void delete(String id) {
        stored.remove(id);
    }

    @Override
    public void deleteAll() {
        stored.clear();
    }
}
