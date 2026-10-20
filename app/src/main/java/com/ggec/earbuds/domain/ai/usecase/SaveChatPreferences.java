package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class SaveChatPreferences {

    private final AiSettingsRepository repository;

    public SaveChatPreferences(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(AiChatPreferences preferences) {
        update(ignored -> preferences);
    }

    /**
     * 在仓库的原子更新里读取旧参数再改，连续改两个不同的参数不会互相覆盖。
     */
    public void update(UnaryOperator<AiChatPreferences> change) {
        Objects.requireNonNull(change, "change");
        repository.update(settings -> {
            AiChatPreferences next = change.apply(settings.preferences());
            if (next.systemPrompt().length() > AiChatPreferences.MAX_SYSTEM_PROMPT_LENGTH) {
                throw new AiValidationException(AiValidationError.PROMPT_TOO_LONG);
            }
            return settings.withPreferences(next);
        });
    }
}
