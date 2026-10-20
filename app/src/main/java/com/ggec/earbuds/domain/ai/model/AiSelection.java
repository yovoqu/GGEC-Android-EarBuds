package com.ggec.earbuds.domain.ai.model;

import java.util.Objects;

public record AiSelection(String platformId, String modelId) {

    public AiSelection {
        Objects.requireNonNull(platformId, "platformId");
        Objects.requireNonNull(modelId, "modelId");
    }
}
