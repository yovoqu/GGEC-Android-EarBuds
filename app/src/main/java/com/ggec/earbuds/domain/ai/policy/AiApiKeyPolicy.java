package com.ggec.earbuds.domain.ai.policy;

import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;

import java.util.regex.Pattern;

/**
 * 密钥会原样放进 HTTP 请求头，所以只接受可见 ASCII。粘贴时常带进来的引号、
 * "Bearer " 前缀、首尾空白和零宽字符，在这里统一清理或拒绝。
 */
public final class AiApiKeyPolicy {

    static final int MAX_LENGTH = 512;
    private static final Pattern BEARER_PREFIX = Pattern.compile("^bearer(\\s+|$)", Pattern.CASE_INSENSITIVE);

    public String normalize(String raw) {
        String key = raw == null ? "" : raw.strip();
        key = unquote(key);
        key = BEARER_PREFIX.matcher(key).replaceFirst("").strip();
        if (key.isEmpty()) {
            throw new AiValidationException(AiValidationError.API_KEY_EMPTY);
        }
        if (key.length() > MAX_LENGTH) {
            throw new AiValidationException(AiValidationError.API_KEY_INVALID);
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c <= ' ' || c >= 0x7f) {
                throw new AiValidationException(AiValidationError.API_KEY_INVALID);
            }
        }
        return key;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if (first == last && (first == '"' || first == '\'' || first == '`')) {
                return value.substring(1, value.length() - 1).strip();
            }
        }
        return value;
    }
}
