package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiRealtimeDialect;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 改语音服务的接口地址、选中模型和线路方言。不碰 Key，也不替用户决定拉哪一家。
 *
 * <p>模型选择对所有服务商开放：谁的服务商能拉到模型，用户就能挑一个。方言仍只有
 * ChatGPT 这条线有，别的服务商传上来也无处可用，取设置时自然保持默认。</p>
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
        repository.update(settings -> settings.withVoice(settings.voice().withRealtimeModel(modelId)));
    }

    public void dialect(@NonNull String dialectId) {
        AiRealtimeDialect dialect = AiRealtimeDialect.fromId(dialectId);
        if (dialect == null) {
            throw new IllegalArgumentException("unknown realtime dialect");
        }
        repository.update(settings -> settings.withVoice(settings.voice().withDialect(dialect)));
    }
}
