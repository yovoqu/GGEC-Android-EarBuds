package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.port.AiUiPreferences;

/** 内存版界面偏好：记录设置页最后停留的分段。 */
public final class MemoryAiUiPreferences implements AiUiPreferences {

    @Nullable
    private AiInteractionMode mode;

    public MemoryAiUiPreferences() {
    }

    public MemoryAiUiPreferences(@NonNull AiInteractionMode initial) {
        this.mode = initial;
    }

    @NonNull
    @Override
    public AiInteractionMode lastSettingsMode(@NonNull AiInteractionMode fallback) {
        return mode == null ? fallback : mode;
    }

    @Override
    public void setLastSettingsMode(@NonNull AiInteractionMode mode) {
        this.mode = mode;
    }
}
