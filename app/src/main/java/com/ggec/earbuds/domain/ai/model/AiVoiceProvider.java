package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 一家语音服务商自己的连接状态：改过的地址、选中的实时模型、线路方言、已拉到的实时模型。
 *
 * <p>这些数据都属于"这一家"，不属于"当前选中的那一家"。把它们从 {@link AiVoiceConfig}
 * 的公共字段搬进按厂商索引的这张表，切换服务商时只换指针，不会再把上一家的结果清掉。</p>
 *
 * <p>Key 不在这里：密钥只在加密存储里，领域只保存"是否已配置"的事实；
 * 明文由 {@code AiCredentialStore} 在建立连接/拉取模型时按需提供。</p>
 */
public record AiVoiceProvider(
        @NonNull String baseUrl,
        @NonNull String realtimeModelId,
        @NonNull List<AiModel> realtimeModels) {

    /** 一家还没配置过的服务商：地址用预设、模型用预设、没有拉过列表。 */
    public static final AiVoiceProvider EMPTY = new AiVoiceProvider("", "", List.of());

    public AiVoiceProvider {
        baseUrl = normalizeBaseUrl(baseUrl);
        realtimeModelId = realtimeModelId == null ? "" : realtimeModelId.strip();
        realtimeModels = cleanModels(realtimeModels);
    }

    public boolean isEmpty() {
        return baseUrl.isEmpty()
                && realtimeModelId.isEmpty()
                && realtimeModels.isEmpty();
    }

    @NonNull
    static String normalizeBaseUrl(@NonNull String url) {
        String trimmed = url == null ? "" : url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** 去重、丢空 id；同一家重复拉取时以新列表为准。 */
    @NonNull
    static List<AiModel> cleanModels(@NonNull List<AiModel> models) {
        List<AiModel> cleaned = new ArrayList<>();
        for (AiModel model : models) {
            if (model == null || model.id().isEmpty() || contains(cleaned, model.id())) {
                continue;
            }
            cleaned.add(model);
        }
        return List.copyOf(cleaned);
    }

    static boolean contains(@NonNull List<AiModel> models, @NonNull String id) {
        for (AiModel model : models) {
            if (model.id().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
