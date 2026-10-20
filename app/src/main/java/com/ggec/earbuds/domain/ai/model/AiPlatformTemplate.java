package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 一类可以配置的平台。自定义模板的主机地址由用户填写，并且可以建多个实例。
 */
public record AiPlatformTemplate(
        String id,
        String name,
        String defaultBaseUrl,
        List<AiProtocol> protocols,
        boolean custom,
        String keyHint,
        @Nullable String consoleUrl) {

    public AiPlatformTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(defaultBaseUrl, "defaultBaseUrl");
        protocols = List.copyOf(protocols);
        if (protocols.isEmpty()) {
            throw new IllegalArgumentException("protocols must not be empty");
        }
        Objects.requireNonNull(keyHint, "keyHint");
    }

    public AiProtocol defaultProtocol() {
        return protocols.get(0);
    }
}
