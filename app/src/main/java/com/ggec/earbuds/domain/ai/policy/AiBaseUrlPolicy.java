package com.ggec.earbuds.domain.ai.policy;

import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 主机地址只接受 https：密钥随每个请求发出，明文传输等于泄露。
 * 没写协议头时补上 https://，其余整理交给目录端口，保证和请求拼路径的规则一致。
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
            throw new AiValidationException(AiValidationError.BASE_URL_NOT_HTTPS);
        }
        if (!lower.startsWith("https://")) {
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
}
