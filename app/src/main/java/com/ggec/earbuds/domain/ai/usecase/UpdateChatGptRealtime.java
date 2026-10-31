package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiRealtimeDialect;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 改 ChatGPT Realtime 的地址、选中模型和方言。不碰 Key，也不替用户决定拉哪一家。
 */
public final class UpdateChatGptRealtime {

    private final AiSettingsRepository repository;

    public UpdateChatGptRealtime(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void baseUrl(@NonNull String url) {
        repository.update(settings -> settings.withVoice(settings.voice().withBaseUrl(url)));
    }

    public void model(@NonNull String modelId) {
        repository.update(settings -> {
            if (settings.voice().model() != AiVoiceModel.CHATGPT) {
                throw new IllegalArgumentException("realtime model selection is for ChatGPT");
            }
            return settings.withVoice(settings.voice().withRealtimeModel(modelId));
        });
    }

    public void dialect(@NonNull String dialectId) {
        AiRealtimeDialect dialect = AiRealtimeDialect.fromId(dialectId);
        if (dialect == null) {
            throw new IllegalArgumentException("unknown realtime dialect");
        }
        repository.update(settings -> settings.withVoice(settings.voice().withDialect(dialect)));
    }
}
