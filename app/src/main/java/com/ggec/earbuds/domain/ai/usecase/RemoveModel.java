package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 从平台的模型列表里移除一个模型。拉取来的模型下次重新拉取时还会回来，手动添加的则彻底删掉。
 */
public final class RemoveModel {

    private final AiSettingsRepository repository;

    public RemoveModel(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(String platformId, String modelId) {
        repository.update(settings -> settings.platform(platformId)
                .map(platform -> {
                    List<AiModel> next = new ArrayList<>(platform.models());
                    next.removeIf(model -> model.id().equals(modelId));
                    AiPlatformConfig updated = platform.withModels(next, platform.modelsFetchedAtMillis());
                    return settings.withPlatform(updated).withValidSelection();
                })
                .orElse(settings));
    }
}
