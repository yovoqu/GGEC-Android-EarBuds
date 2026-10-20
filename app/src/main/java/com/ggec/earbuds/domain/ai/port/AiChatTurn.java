package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiRole;

import java.util.Objects;

public record AiChatTurn(AiRole role, String text) {

    public AiChatTurn {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
    }
}
