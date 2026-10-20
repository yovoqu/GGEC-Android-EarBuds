package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiProtocol;

import java.util.List;

/**
 * AI 设置总览页的界面状态。
 */
public record AiSettingsUiState(
        boolean loaded,
        @Nullable ActiveModel activeModel,
        List<PlatformRow> platforms,
        Preferences preferences) {

    public record ActiveModel(String platformId, String platformName, String modelName, AiProtocol protocol) {
    }

    /**
     * 平台列表的一行。内置平台即使还没配置也占一行，{@code platformId} 为空表示尚未创建实例。
     */
    public record PlatformRow(
            String templateId,
            @Nullable String platformId,
            String name,
            boolean custom,
            boolean configured,
            int modelCount,
            boolean active) {
    }

    public record Preferences(
            String systemPrompt,
            boolean systemPromptIsDefault,
            @Nullable Double temperature,
            @Nullable Integer maxOutputTokens,
            int contextMessages,
            boolean showReasoning) {
    }

    public AiSettingsUiState {
        platforms = List.copyOf(platforms);
    }
}
