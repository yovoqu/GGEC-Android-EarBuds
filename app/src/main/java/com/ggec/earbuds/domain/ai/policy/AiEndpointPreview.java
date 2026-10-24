package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;

/**
 * 请求地址预览（ADV-02）：与 lib_ai 实际请求一致——先规范化接口地址（去掉结尾的 /v1、完整端点等），
 * 再拼 {@code /v1} 和协议路径。
 */
public final class AiEndpointPreview {

    private AiEndpointPreview() {
    }

    public static String path(AiProtocol protocol) {
        return switch (protocol) {
            case CHAT_COMPLETIONS -> "/v1/chat/completions";
            case RESPONSES -> "/v1/responses";
            case MESSAGES -> "/v1/messages";
        };
    }

    /** 地址为空或不合法时返回空。 */
    @Nullable
    public static String of(AiPlatformCatalog catalog, @Nullable String baseUrl, AiProtocol protocol) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            return catalog.normalizeBaseUrl(baseUrl.strip()) + path(protocol);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    public static boolean valid(AiPlatformCatalog catalog, @Nullable String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return true;
        }
        try {
            catalog.normalizeBaseUrl(baseUrl.strip());
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    /** 摘要里显示的主机名。 */
    @Nullable
    public static String host(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String rest = url.strip();
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            rest = rest.substring(scheme + 3);
        }
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }
}
