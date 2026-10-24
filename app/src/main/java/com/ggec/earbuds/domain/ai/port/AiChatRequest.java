package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;

import java.util.List;
import java.util.Objects;

/**
 * 发给网关的一次对话请求。最后一轮一定是用户发言。
 */
public record AiChatRequest(
        AiPlatformConfig platform,
        String modelId,
        @Nullable String systemPrompt,
        List<AiChatTurn> turns,
        @Nullable Double temperature,
        @Nullable Double topP,
        @Nullable Integer maxOutputTokens) {

    public AiChatRequest {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(modelId, "modelId");
        turns = List.copyOf(turns);
        if (turns.isEmpty()) {
            throw new IllegalArgumentException("turns must not be empty");
        }
    }
}
