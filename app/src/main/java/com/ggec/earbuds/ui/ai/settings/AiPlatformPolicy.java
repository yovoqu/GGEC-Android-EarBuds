package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;

import java.util.List;

/** 平台页的命令决策：只做判断，不持有状态，也不发副作用。 */
final class AiPlatformPolicy {

    private AiPlatformPolicy() {
    }

    @Nullable
    static String selectedModelOf(@NonNull AiSettings settings, @NonNull AiPlatformConfig platform) {
        AiSelection selection = settings.textSelection();
        return selection != null && platform.id().equals(selection.platformId())
                ? selection.modelId() : null;
    }

    @NonNull
    static List<String> chatModelIds(@NonNull AiPlatformConfig platform) {
        return platform.chatModels().stream().map(AiModel::id).toList();
    }

    @NonNull
    static String normalizeSearch(@NonNull String raw) {
        return raw.strip();
    }
}
