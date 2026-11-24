package com.yovo.biz_ggec_record.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Record 产品目录项（型号真源投影）。
 */
public final class RecordProduct {

    @NonNull
    private final String modelId;
    @NonNull
    private final String displayName;

    public RecordProduct(@NonNull String modelId, @NonNull String displayName) {
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        if (modelId.isEmpty() || displayName.isEmpty()) {
            throw new IllegalArgumentException("RecordProduct requires modelId and displayName");
        }
    }

    @NonNull
    public String modelId() {
        return modelId;
    }

    @NonNull
    public String displayName() {
        return displayName;
    }
}
