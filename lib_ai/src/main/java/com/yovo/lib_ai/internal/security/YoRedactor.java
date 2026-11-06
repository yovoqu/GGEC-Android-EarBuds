package com.yovo.lib_ai.internal.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * 日志/异常脱敏。所有对外可见的文本在落日志前都应经过这里。
 *
 * <p>只做保守替换：不试图解析 JSON 结构，避免把正常内容改坏；目标是把
 * Authorization、sk- 密钥、data URL 的大段 base64 隐藏掉。</p>
 */
public final class YoRedactor {

    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Pattern SK_KEY = Pattern.compile("\\bsk-[A-Za-z0-9._\\-]{8,}");
    private static final Pattern DATA_URL =
            Pattern.compile("data:([A-Za-z0-9.+\\-/]+);base64,[A-Za-z0-9+/=\\r\\n]{16,}");
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)\"(api[_-]?key|authorization|access[_-]?token|secret)\"\\s*:\\s*\"[^\"]*\"");

    private YoRedactor() {
    }

    @NonNull
    public static String redact(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = BEARER.matcher(text).replaceAll("Bearer ***");
        result = SK_KEY.matcher(result).replaceAll("sk-***");
        result = DATA_URL.matcher(result).replaceAll("data:$1;base64,***");
        result = JSON_SECRET.matcher(result).replaceAll("\"$1\":\"***\"");
        return result;
    }
}
