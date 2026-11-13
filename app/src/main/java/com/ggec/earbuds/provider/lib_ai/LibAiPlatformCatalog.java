package com.ggec.earbuds.provider.lib_ai;

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
        return normalizeForRequest(raw);
    }

    /**
     * 请求用的最终地址：先按平台目录归一（去尾斜杠、补协议），再补厂商自己的兼容路径。
     *
     * <p>厂商路径是<b>平台知识</b>，留在目录这一层；网关只拿结果去发请求，不再各自写一份
     * "如果是 dashscope 就..."。</p>
     */
    static String normalizeForRequest(String raw) {
        String normalized = YoPlatformCatalog.normalizeBaseUrl(raw == null ? "" : raw);
        if (normalized.isEmpty()) {
            return "";
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        // DashScope 的 OpenAI 兼容接口（含 /models）挂在 /compatible-mode/v1 下。
        if (lower.contains("dashscope") || lower.contains("aliyuncs")) {
            if (lower.endsWith("/compatible-mode")) {
                return normalized + "/v1";
            }
            if (!lower.endsWith("/compatible-mode/v1")) {
                return normalized + "/compatible-mode/v1";
            }
        }
        return normalized;
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

    private static AiPlatformTemplate toTemplate(YoPlatformDescriptor descriptor) {
        List<AiProtocol> protocols = new ArrayList<>();
        for (YoProtocol protocol : descriptor.protocols()) {
            protocols.add(protocol(protocol));
        }
        return new AiPlatformTemplate(descriptor.templateId(), descriptor.displayName(),
                descriptor.defaultBaseUrl(), protocols, descriptor.custom(), descriptor.apiKeyHint(),
                descriptor.consoleUrl());
    }
}
