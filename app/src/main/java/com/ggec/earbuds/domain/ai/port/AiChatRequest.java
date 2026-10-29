package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;

import java.util.List;
import java.util.Objects;

/**
 * 发给网关的一次对话请求。最后一轮一定是用户发言。
 *
 * <p>这里只有"说给模型听的内容"：平台、模型、系统提示词、历史。采样参数不在这里，
 * 因为用户已经不能调它们了——由网关在真正发请求时统一填
 * {@link com.ggec.earbuds.domain.ai.model.AiGenerationDefaults}。</p>
 */
public record AiChatRequest(
        AiPlatformConfig platform,
        String modelId,
        @Nullable String systemPrompt,
        List<AiChatTurn> turns) {

    public AiChatRequest {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(modelId, "modelId");
        turns = List.copyOf(turns);
        if (turns.isEmpty()) {
            throw new IllegalArgumentException("turns must not be empty");
        }
    }
}
