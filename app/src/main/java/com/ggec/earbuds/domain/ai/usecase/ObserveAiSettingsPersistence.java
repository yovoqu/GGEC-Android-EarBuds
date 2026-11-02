package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;

import java.util.Objects;
import java.util.function.Consumer;

/** 观察设置的磁盘持久化状态，供 ViewModel 展示“保存中 / 已保存 / 保存失败”。 */
public final class ObserveAiSettingsPersistence {

    private final AiSettingsRepository repository;

    public ObserveAiSettingsPersistence(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Subscription execute(Consumer<AiSettingsPersistenceStatus> observer) {
        return repository.observePersistenceStatus(Objects.requireNonNull(observer, "observer"));
    }
}
