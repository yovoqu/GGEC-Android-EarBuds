package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiUsage;

import java.util.Objects;

public record AiChatResult(
        String text,
        @Nullable String reasoning,
        AiFinishReason finishReason,
        @Nullable AiUsage usage) {

    public AiChatResult {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(finishReason, "finishReason");
    }
}
