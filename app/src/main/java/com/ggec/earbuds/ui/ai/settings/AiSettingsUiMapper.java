package com.ggec.earbuds.ui.ai.settings;

import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AiSettingsUiMapper {

    /**
     * 先列内置平台（不论配没配），再列用户添加的自定义平台。
     */
    public AiSettingsUiState map(AiSettings settings, List<AiPlatformTemplate> templates) {
        Optional<AiActiveModel> active = settings.activeModel();
        String activePlatformId = active.map(model -> model.platform().id()).orElse(null);
        List<AiSettingsUiState.PlatformRow> rows = new ArrayList<>();
        for (AiPlatformTemplate template : templates) {
            if (template.custom()) {
                continue;
            }
            Optional<AiPlatformConfig> config = settings.platform(template.id());
            rows.add(new AiSettingsUiState.PlatformRow(
                    template.id(),
                    config.map(AiPlatformConfig::id).orElse(null),
                    template.name(),
                    false,
                    config.map(AiPlatformConfig::configured).orElse(false),
                    config.map(platform -> platform.models().size()).orElse(0),
                    template.id().equals(activePlatformId)));
        }
        for (AiPlatformConfig platform : settings.platforms()) {
            if (!isCustom(platform, templates)) {
                continue;
            }
            rows.add(new AiSettingsUiState.PlatformRow(
                    platform.templateId(),
                    platform.id(),
                    platform.name(),
                    true,
                    platform.configured(),
                    platform.models().size(),
                    platform.id().equals(activePlatformId)));
        }
        return new AiSettingsUiState(
                true,
                active.map(model -> new AiSettingsUiState.ActiveModel(
                        model.platform().id(), model.platform().name(), model.model().name(),
                        model.platform().protocol())).orElse(null),
                rows,
                preferences(settings.preferences()));
    }

    private static boolean isCustom(AiPlatformConfig platform, List<AiPlatformTemplate> templates) {
        for (AiPlatformTemplate template : templates) {
            if (template.id().equals(platform.templateId())) {
                return template.custom();
            }
        }
        return true;
    }

    private static AiSettingsUiState.Preferences preferences(AiChatPreferences preferences) {
        return new AiSettingsUiState.Preferences(
                preferences.systemPrompt(),
                preferences.systemPrompt().equals(AiChatPreferences.DEFAULT_SYSTEM_PROMPT),
                preferences.temperature(),
                preferences.maxOutputTokens(),
                preferences.contextMessages(),
                preferences.showReasoning());
    }
}
