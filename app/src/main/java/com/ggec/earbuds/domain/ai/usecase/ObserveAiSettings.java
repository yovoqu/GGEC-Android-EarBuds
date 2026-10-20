package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;

import java.util.Objects;
import java.util.function.Consumer;

public final class ObserveAiSettings {

    private final AiSettingsRepository repository;

    public ObserveAiSettings(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Subscription execute(Consumer<AiSettings> observer) {
        return repository.observe(Objects.requireNonNull(observer, "observer"));
    }
}
