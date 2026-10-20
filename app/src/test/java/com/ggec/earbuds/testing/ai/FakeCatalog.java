package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;

import java.util.List;

/** 两个模板：一个内置的 DeepSeek，一个自定义的 OpenAI 兼容。地址整理只去掉尾部斜杠和 /v1。 */
public final class FakeCatalog implements AiPlatformCatalog {

    public static final AiPlatformTemplate DEEPSEEK = new AiPlatformTemplate(
            "deepseek", "DeepSeek", "https://api.deepseek.com",
            List.of(AiProtocol.CHAT_COMPLETIONS, AiProtocol.MESSAGES), false, "sk-…", null);
    public static final AiPlatformTemplate COMPATIBLE = new AiPlatformTemplate(
            "openai-compatible", "OpenAI Compatible", "",
            List.of(AiProtocol.CHAT_COMPLETIONS, AiProtocol.RESPONSES), true, "sk-…", null);

    @Override
    public List<AiPlatformTemplate> templates() {
        return List.of(DEEPSEEK, COMPATIBLE);
    }

    @Override
    public String normalizeBaseUrl(String raw) {
        String value = raw.trim();
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            throw new IllegalArgumentException("scheme");
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/v1")) {
            value = value.substring(0, value.length() - 3);
        }
        if (value.equals("https:") || value.equals("http:") || value.endsWith("//")) {
            throw new IllegalArgumentException("host");
        }
        return value;
    }
}
