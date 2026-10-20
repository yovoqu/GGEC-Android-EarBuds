package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiModelGateway;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 向平台拉取模型列表并写回配置。平台上的列表为准，用户手动添加的模型保留；
 * 已选中的模型如果从平台列表里消失，选中项一并失效。阻塞调用，不要在主线程执行。
 */
public final class FetchPlatformModels {

    private final AiSettingsRepository repository;
    private final AiModelGateway gateway;
    private final AiClock clock;

    public FetchPlatformModels(AiSettingsRepository repository, AiModelGateway gateway, AiClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AiPlatformConfig execute(String platformId) {
        AiPlatformConfig platform = PlatformLookup.configured(repository.load(), platformId);
        List<AiModel> fetched = gateway.fetch(platform);
        long fetchedAt = clock.now();
        AtomicReference<AiPlatformConfig> saved = new AtomicReference<>(platform);
        repository.update(settings -> settings.platform(platformId)
                .map(current -> {
                    AiPlatformConfig updated = current.withModels(merge(fetched, current.models()), fetchedAt);
                    saved.set(updated);
                    return settings.withPlatform(updated).withValidSelection();
                })
                .orElse(settings));
        return saved.get();
    }

    static List<AiModel> merge(List<AiModel> fetched, List<AiModel> previous) {
        Map<String, AiModel> byId = new LinkedHashMap<>();
        for (AiModel model : fetched) {
            byId.put(model.id(), model);
        }
        for (AiModel model : previous) {
            if (model.manual()) {
                byId.putIfAbsent(model.id(), model);
            }
        }
        return List.copyOf(byId.values());
    }
}
