package com.ggec.earbuds.domain.ai.policy;


import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 主机地址的整理与放行规则。
 *
 * <p>公网只接受 https：密钥随每个请求发出，明文过公网等于把密钥交出去。
 * 回环与私有网段放行 http —— 自建模型服务（Ollama、LM Studio、vLLM、one-api）绝大多数只有
 * 局域网 http，一刀切拒绝会让"接自己的机器"这件事根本做不到。放行的地址由
 * {@link #isPlainText(String)} 标出来，界面据此提示"此地址不加密传输"。</p>
 */
public final class AiBaseUrlPolicy {

    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");

    private final AiPlatformCatalog catalog;

    public AiBaseUrlPolicy(AiPlatformCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * @return 整理后的主机基址；输入为空且不要求填写时返回 null，表示使用模板的官方地址。
     */
    public String normalize(String raw, boolean required) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            if (required) {
                throw new AiValidationException(AiValidationError.BASE_URL_REQUIRED);
            }
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) {
            if (!isPrivateHost(value)) {
                throw new AiValidationException(AiValidationError.BASE_URL_NOT_HTTPS);
            }
        } else if (!lower.startsWith("https://")) {
            if (SCHEME.matcher(value).matches()) {
                throw new AiValidationException(AiValidationError.BASE_URL_INVALID);
            }
            value = "https://" + value;
        }
        try {
            return catalog.normalizeBaseUrl(value);
        } catch (IllegalArgumentException invalid) {
            throw new AiValidationException(AiValidationError.BASE_URL_INVALID);
        }
    }

    /** 这个地址会不会明文传输，界面据此提示用户。 */
    public static boolean isPlainText(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).startsWith("http://");
    }

    /**
     * 回环、私有网段、以及不带点的主机名（局域网里的机器名）。
     * 公网 IP 与公网域名一律返回 false。
     */
    static boolean isPrivateHost(String url) {
        String host = hostOf(url);
        if (host == null || host.isEmpty()) {
            return false;
        }
        String normalized = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        if ("localhost".equals(normalized) || "::1".equals(normalized)) {
            return true;
        }
        if (!normalized.contains(".")) {
            // 单标签主机名：局域网里常见的 "nas"、"gpu-box"，解析范围由本地 DNS 决定。
            return true;
        }
        String[] parts = normalized.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            Integer.parseInt(parts[2]);
            Integer.parseInt(parts[3]);
            return a == 10
                    || a == 127
                    || (a == 192 && b == 168)
                    || (a == 172 && b >= 16 && b <= 31);
        } catch (NumberFormatException notAnIp) {
            return false;
        }
    }

    private static String hostOf(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        String rest = url.substring(schemeEnd + 3);
        int slash = rest.indexOf('/');
        String authority = slash < 0 ? rest : rest.substring(0, slash);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            return close < 0 ? null : authority.substring(0, close + 1);
        }
        int colon = authority.indexOf(':');
        return colon < 0 ? authority : authority.substring(0, colon);
    }
}
