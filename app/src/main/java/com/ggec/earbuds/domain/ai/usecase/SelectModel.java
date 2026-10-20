package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 选定对话使用的模型。平台必须已配置密钥，模型必须在该平台的列表里。
 */
public final class SelectModel {

    private final AiSettingsRepository repository;

    public SelectModel(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(String platformId, String modelId) {
        repository.update(settings -> {
            AiPlatformConfig platform = PlatformLookup.configured(settings, platformId);
            if (platform.model(modelId).isEmpty()) {
                throw new AiValidationException(AiValidationError.MODEL_UNKNOWN);
            }
            return settings.withSelection(new AiSelection(platformId, modelId));
        });
    }
}
