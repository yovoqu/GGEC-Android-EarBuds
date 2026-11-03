package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;

import java.util.function.UnaryOperator;

/**
 * 对 {@link AiSettings} 的一组纯变更函数。
 *
 * <p>设置页的"本地先落地"和"节流后落盘"必须应用同一份规则，否则会出现
 * "界面成功、磁盘拒绝"的两套行为。这里把每条配置变更集中成纯函数，视图模型只负责
 * 组合与调度，仓库只负责执行；不再为每个字段各留一个只做 {@code repository.update}
 * 的用例包装。</p>
 */
public final class AiSettingsChanges {

    private AiSettingsChanges() {
    }

    @NonNull
    public static UnaryOperator<AiSettings> systemPrompt(@NonNull String prompt) {
        return settings -> {
            AiChatPreferences next = settings.preferences().withSystemPrompt(prompt);
            if (next.systemPrompt().length() > AiChatPreferences.MAX_SYSTEM_PROMPT_LENGTH) {
                throw new AiValidationException(AiValidationError.PROMPT_TOO_LONG);
            }
            return settings.withPreferences(next);
        };
    }

    @NonNull
    public static UnaryOperator<AiSettings> voiceModel(@NonNull AiVoiceModel model) {
        return settings -> settings.withVoice(settings.voice().withModel(model));
    }

    @NonNull
    public static UnaryOperator<AiSettings> voiceApiKey(@NonNull String apiKey) {
        return settings -> settings.withVoice(settings.voice().withApiKey(apiKey));
    }

    /** 空串恢复官方地址；非法地址抛 {@link IllegalArgumentException}。 */
    @NonNull
    public static UnaryOperator<AiSettings> voiceBaseUrl(@NonNull String baseUrl) {
        return settings -> settings.withVoice(settings.voice().withBaseUrl(baseUrl));
    }

    /** 只在已拉取的模型列表里选；不在列表里抛 {@link IllegalArgumentException}。 */
    @NonNull
    public static UnaryOperator<AiSettings> voiceRealtimeModel(@NonNull String modelId) {
        return settings -> settings.withVoice(settings.voice().withRealtimeModel(modelId));
    }

    @NonNull
    public static UnaryOperator<AiSettings> voiceDialect(@NonNull AiRealtimeDialect dialect) {
        return settings -> settings.withVoice(settings.voice().withDialect(dialect));
    }
}
