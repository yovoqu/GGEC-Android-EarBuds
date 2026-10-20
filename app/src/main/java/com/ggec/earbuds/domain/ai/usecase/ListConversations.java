package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;

import java.util.List;
import java.util.Objects;

public final class ListConversations {

    private final AiConversationRepository repository;

    public ListConversations(AiConversationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<AiConversationSummary> execute() {
        return List.copyOf(repository.summaries());
    }
}
