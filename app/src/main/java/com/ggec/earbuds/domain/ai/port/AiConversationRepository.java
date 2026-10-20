package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;

import java.util.List;
import java.util.Optional;

public interface AiConversationRepository {

    /**
     * 按更新时间从新到旧。
     */
    List<AiConversationSummary> summaries();

    Optional<AiConversation> find(String id);

    void save(AiConversation conversation);

    void delete(String id);

    void deleteAll();
}
