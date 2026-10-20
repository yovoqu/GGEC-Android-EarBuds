package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

public final class LoadAiSettings {

    private final AiSettingsRepository repository;

    public LoadAiSettings(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public AiSettings execute() {
        return repository.load();
    }
}
