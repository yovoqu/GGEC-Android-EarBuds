package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 语音交流模式的配置。
 *
 * <p>厂商（{@link AiVoiceModel}）决定默认主机和兜底模型。ChatGPT 这一家还可以改接口地址、
 * 从拉取到的实时模型里选一个、并选线路方言。这些选择存在设置文档里；Key 仍只在加密存储。</p>
 */
public record AiVoiceConfig(
        @NonNull AiVoiceModel model,
        @NonNull Map<AiVoiceModel, String> apiKeys,
        @NonNull String baseUrl,
        @NonNull String realtimeModelId,
        @NonNull AiRealtimeDialect dialect,
        @NonNull List<String> realtimeModels) {

    public static final AiVoiceConfig DEFAULT = new AiVoiceConfig(AiVoiceModel.defaultModel(), Map.of());

    /** 旧构造：只选厂商和 Key。地址、模型和方言用默认，已有测试和文档走这条。 */
    public AiVoiceConfig(@NonNull AiVoiceModel model, @NonNull Map<AiVoiceModel, String> apiKeys) {
        this(model, apiKeys, "", "", AiRealtimeDialect.defaultDialect(), List.of());
    }

    public AiVoiceConfig {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(dialect, "dialect");
        EnumMap<AiVoiceModel, String> copy = new EnumMap<>(AiVoiceModel.class);
        for (Map.Entry<AiVoiceModel, String> entry : apiKeys.entrySet()) {
            String key = entry.getValue() == null ? "" : entry.getValue().strip();
            if (entry.getKey() != null && !key.isEmpty()) {
                copy.put(entry.getKey(), key);
            }
        }
        apiKeys = Collections.unmodifiableMap(copy);
        baseUrl = normalizeBaseUrl(baseUrl);
        realtimeModelId = realtimeModelId == null ? "" : realtimeModelId.strip();
        realtimeModels = cleanIds(realtimeModels);
    }

    /** 当前选中的模型用的 Key；没填时是空串。 */
    @NonNull
    public String apiKey() {
        return apiKeys.getOrDefault(model, "");
    }

    /** 只选了模型还不算配好：没有 Key 就连不上，通话入口不该放出去。 */
    public boolean configured() {
        return !apiKey().isBlank();
    }

    /** 真正去连的主机。没改过地址时用厂商预设。 */
    @NonNull
    public String effectiveBaseUrl() {
        return baseUrl.isEmpty() ? model.baseUrl() : baseUrl;
    }

    /**
     * 真正去连的实时模型。ChatGPT 选过拉取结果时用那个 id，否则用厂商预设。
     */
    @NonNull
    public String resolvedRealtimeModel() {
        if (model == AiVoiceModel.CHATGPT && !realtimeModelId.isEmpty()) {
            return realtimeModelId;
        }
        return model.realtimeModel();
    }

    @NonNull
    public AiVoiceConfig withModel(@NonNull AiVoiceModel next) {
        Objects.requireNonNull(next, "model");
        return next == model ? this : copy(next, apiKeys, baseUrl, realtimeModelId, dialect, realtimeModels);
    }

    /** 给当前模型换 Key；空串表示清掉这一家的 Key。 */
    @NonNull
    public AiVoiceConfig withApiKey(@NonNull String key) {
        EnumMap<AiVoiceModel, String> next = new EnumMap<>(AiVoiceModel.class);
        next.putAll(apiKeys);
        String trimmed = key.strip();
        if (trimmed.isEmpty()) {
            next.remove(model);
        } else {
            next.put(model, trimmed);
        }
        return copy(model, next, baseUrl, realtimeModelId, dialect, realtimeModels);
    }

    /**
     * 改 ChatGPT 的接口地址。空串恢复成厂商预设。必须是 http 或 https。
     */
    @NonNull
    public AiVoiceConfig withBaseUrl(@NonNull String url) {
        String normalized = normalizeBaseUrl(url);
        if (!normalized.isEmpty()
                && !normalized.startsWith("https://")
                && !normalized.startsWith("http://")) {
            throw new IllegalArgumentException("baseUrl must start with http:// or https://");
        }
        return copy(model, apiKeys, normalized, realtimeModelId, dialect, realtimeModels);
    }

    @NonNull
    public AiVoiceConfig withDialect(@NonNull AiRealtimeDialect next) {
        Objects.requireNonNull(next, "dialect");
        return copy(model, apiKeys, baseUrl, realtimeModelId, next, realtimeModels);
    }

    @NonNull
    public AiVoiceConfig withRealtimeModel(@NonNull String modelId) {
        String id = modelId.strip();
        if (id.isEmpty() || !realtimeModels.contains(id)) {
            throw new IllegalArgumentException("realtime model is not in the fetched list");
        }
        return copy(model, apiKeys, baseUrl, id, dialect, realtimeModels);
    }

    /**
     * 用刚拉到的实时模型替换候选。当前选择还在新列表里就留着，否则改选第一项。
     */
    @NonNull
    public AiVoiceConfig withRealtimeModels(@NonNull List<String> ids) {
        List<String> cleaned = cleanIds(ids);
        String selected = realtimeModelId;
        if (!cleaned.contains(selected)) {
            selected = cleaned.isEmpty() ? "" : cleaned.get(0);
        }
        return copy(model, apiKeys, baseUrl, selected, dialect, cleaned);
    }

    @NonNull
    private AiVoiceConfig copy(
            AiVoiceModel nextModel,
            Map<AiVoiceModel, String> nextKeys,
            String nextBaseUrl,
            String nextRealtimeModelId,
            AiRealtimeDialect nextDialect,
            List<String> nextModels) {
        return new AiVoiceConfig(nextModel, nextKeys, nextBaseUrl, nextRealtimeModelId, nextDialect, nextModels);
    }

    @NonNull
    private static String normalizeBaseUrl(@NonNull String url) {
        String trimmed = url == null ? "" : url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @NonNull
    private static List<String> cleanIds(@NonNull List<String> ids) {
        List<String> cleaned = new ArrayList<>();
        for (String id : ids) {
            if (id == null) {
                continue;
            }
            String trimmed = id.strip();
            if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        return List.copyOf(cleaned);
    }
}
