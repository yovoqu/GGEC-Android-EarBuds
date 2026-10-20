package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

public final class RemovePlatform {

    private final AiSettingsRepository repository;

    public RemovePlatform(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(String platformId) {
        repository.update(settings -> settings.withoutPlatform(platformId));
    }
}
