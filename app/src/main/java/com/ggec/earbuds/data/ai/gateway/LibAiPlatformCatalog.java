package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.catalog.YoPlatformCatalog;
import com.yovo.lib_ai.catalog.YoPlatformDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台目录端口的 lib_ai 实现。目录内容完全来自 {@link YoPlatformCatalog}，应用侧不重复维护平台清单。
 */
public final class LibAiPlatformCatalog implements AiPlatformCatalog {

    private final List<AiPlatformTemplate> templates;

    public LibAiPlatformCatalog() {
        List<AiPlatformTemplate> mapped = new ArrayList<>();
        for (YoPlatformDescriptor descriptor : YoPlatformCatalog.standard().descriptors()) {
            mapped.add(toTemplate(descriptor));
        }
        this.templates = List.copyOf(mapped);
    }

    @Override
    public List<AiPlatformTemplate> templates() {
        return templates;
    }

    @Override
    public String normalizeBaseUrl(String raw) {
        return YoPlatformCatalog.normalizeBaseUrl(raw);
    }

    static AiProtocol protocol(YoProtocol protocol) {
        return AiProtocol.fromId(protocol.id(), AiProtocol.CHAT_COMPLETIONS);
    }

    static YoProtocol protocol(AiProtocol protocol) {
        YoProtocol mapped = YoProtocol.fromId(protocol.id());
        if (mapped == null) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        return mapped;
    }

    /** 模板名就是新平台的默认名（PLT-03）；两个自定义模板在 lib_ai 里是英文名，这里换成界面用的中文名。 */
    static String displayName(YoPlatformDescriptor descriptor) {
        return switch (descriptor.templateId()) {
            case "openai-compatible" -> "自定义 OpenAI 兼容";
            case "anthropic-compatible" -> "自定义 Anthropic 兼容";
            default -> descriptor.displayName();
        };
    }

    private static AiPlatformTemplate toTemplate(YoPlatformDescriptor descriptor) {
        List<AiProtocol> protocols = new ArrayList<>();
        for (YoProtocol protocol : descriptor.protocols()) {
            protocols.add(protocol(protocol));
        }
        return new AiPlatformTemplate(descriptor.templateId(), displayName(descriptor),
                descriptor.defaultBaseUrl(), protocols, descriptor.custom(), descriptor.apiKeyHint(),
                descriptor.consoleUrl());
    }
}
