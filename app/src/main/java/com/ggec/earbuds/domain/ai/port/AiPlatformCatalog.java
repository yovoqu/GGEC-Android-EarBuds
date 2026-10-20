package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;

import java.util.List;
import java.util.Optional;

public interface AiPlatformCatalog {

    List<AiPlatformTemplate> templates();

    default Optional<AiPlatformTemplate> find(String templateId) {
        for (AiPlatformTemplate template : templates()) {
            if (template.id().equals(templateId)) {
                return Optional.of(template);
            }
        }
        return Optional.empty();
    }

    /**
     * 把用户粘贴的地址整理成协议路径之前的主机基址，格式不对抛 {@link IllegalArgumentException}。
     */
    String normalizeBaseUrl(String raw);
}
