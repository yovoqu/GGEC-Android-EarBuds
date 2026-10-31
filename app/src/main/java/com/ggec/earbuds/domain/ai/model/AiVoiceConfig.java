package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 语音交流模式的配置：选哪个预设模型，以及每家模型自己的 API Key。
 *
 * <p>这里刻意没有平台 id、没有模型列表——语音模型由 {@link AiVoiceModel} 在代码里预设，
 * 用户能改的只有"选哪一家"和"填它的 Key"。两家的 Key 各存一份，从 ChatGPT 切到 Qwen
 * 再切回来，原来的 Key 还在，不会出现"切一次就要重填一次"。</p>
 *
 * <p>Key 只存在于内存与加密存储里，设置文档里没有它（见 {@code AiSettingsRepositoryImpl}）。</p>
 */
public record AiVoiceConfig(@NonNull AiVoiceModel model, @NonNull Map<AiVoiceModel, String> apiKeys) {

    public static final AiVoiceConfig DEFAULT = new AiVoiceConfig(AiVoiceModel.defaultModel(), Map.of());

    public AiVoiceConfig {
        Objects.requireNonNull(model, "model");
        EnumMap<AiVoiceModel, String> copy = new EnumMap<>(AiVoiceModel.class);
        for (Map.Entry<AiVoiceModel, String> entry : apiKeys.entrySet()) {
            String key = entry.getValue() == null ? "" : entry.getValue().strip();
            if (entry.getKey() != null && !key.isEmpty()) {
                copy.put(entry.getKey(), key);
            }
        }
        apiKeys = Collections.unmodifiableMap(copy);
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

    @NonNull
    public AiVoiceConfig withModel(@NonNull AiVoiceModel next) {
        Objects.requireNonNull(next, "model");
        return next == model ? this : new AiVoiceConfig(next, apiKeys);
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
        return new AiVoiceConfig(model, next);
    }
}
