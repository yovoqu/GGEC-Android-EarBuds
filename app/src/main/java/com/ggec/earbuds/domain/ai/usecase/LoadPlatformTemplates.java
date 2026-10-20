package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;

import java.util.List;
import java.util.Objects;

public final class LoadPlatformTemplates {

    private final AiPlatformCatalog catalog;

    public LoadPlatformTemplates(AiPlatformCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public List<AiPlatformTemplate> execute() {
        return List.copyOf(catalog.templates());
    }
}
