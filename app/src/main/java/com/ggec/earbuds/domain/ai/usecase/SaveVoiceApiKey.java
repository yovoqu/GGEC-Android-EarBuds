package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 保存当前语音模型的 API Key。空串表示清掉这一家的 Key。
 *
 * <p>Key 只落在加密存储里（仓库实现负责），设置文档中永远没有它。</p>
 */
public final class SaveVoiceApiKey {

    private final AiSettingsRepository repository;

    public SaveVoiceApiKey(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(@NonNull String apiKey) {
        repository.update(settings -> settings.withVoice(settings.voice().withApiKey(apiKey)));
    }
}
