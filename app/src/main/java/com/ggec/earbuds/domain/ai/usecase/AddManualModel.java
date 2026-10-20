package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.policy.AiModelIdPolicy;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 平台不提供模型列表，或者列表里没有想要的模型时，手动输入模型名。已存在的同名模型不重复添加。
 */
public final class AddManualModel {

    private final AiSettingsRepository repository;
    private final AiModelIdPolicy policy;

    public AddManualModel(AiSettingsRepository repository, AiModelIdPolicy policy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public AiModel execute(String platformId, String rawModelId) {
        String modelId = policy.normalize(rawModelId);
        AtomicReference<AiModel> result = new AtomicReference<>();
        repository.update(settings -> {
            AiPlatformConfig platform = PlatformLookup.configured(settings, platformId);
            AiModel existing = platform.model(modelId).orElse(null);
            if (existing != null) {
                result.set(existing);
                return settings;
            }
            AiModel added = AiModel.manual(modelId);
            List<AiModel> next = new ArrayList<>(platform.models());
            next.add(added);
            result.set(added);
            return settings.withPlatform(platform.withModels(next, platform.modelsFetchedAtMillis()));
        });
        return result.get();
    }
}
