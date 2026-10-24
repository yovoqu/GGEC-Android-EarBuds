package com.ggec.earbuds.data.ai.gateway;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;

import java.util.regex.Pattern;

/**
 * 把 lib_ai 的失败折成领域失败。分类规则在 lib_ai 里只有一份（{@link YoAiErrors}），这里只做改名；
 * 平台返回的说明文字可能回显密钥片段，进入领域之前先抹掉。
 */
final class LibAiFailures {

    private static final int MAX_MESSAGE = 300;
    private static final int MIN_REDACTED_KEY = 8;
    private static final Pattern TOKEN_LIKE = Pattern.compile("sk-[A-Za-z0-9_\\-]{8,}");

    private LibAiFailures() {
    }

    static AiFailure map(Throwable error, @Nullable String apiKey) {
        if (error instanceof AiFailure failure) {
            return failure;
        }
        return new AiFailure(kind(YoAiErrors.kindOf(error)), redact(error.getMessage(), apiKey), error);
    }

    static AiFailureKind kind(YoAiErrorKind kind) {
        return switch (kind) {
            case AUTHENTICATION -> AiFailureKind.AUTHENTICATION;
            case PERMISSION -> AiFailureKind.PERMISSION;
            case QUOTA -> AiFailureKind.QUOTA;
            case RATE_LIMITED -> AiFailureKind.RATE_LIMITED;
            case NOT_FOUND -> AiFailureKind.NOT_FOUND;
            case BAD_REQUEST -> AiFailureKind.BAD_REQUEST;
            case SERVER -> AiFailureKind.SERVER;
            case NETWORK -> AiFailureKind.NETWORK;
            case TIMEOUT -> AiFailureKind.TIMEOUT;
            case CANCELLED -> AiFailureKind.CANCELLED;
            case UNSUPPORTED_CAPABILITY -> AiFailureKind.UNSUPPORTED_CAPABILITY;
            case PROTOCOL -> AiFailureKind.PROTOCOL;
            case UNKNOWN -> AiFailureKind.UNKNOWN;
        };
    }

    static String redact(@Nullable String message, @Nullable String apiKey) {
        if (message == null) {
            return "";
        }
        String clean = message;
        if (apiKey != null && apiKey.length() >= MIN_REDACTED_KEY) {
            clean = clean.replace(apiKey, "***");
        }
        clean = TOKEN_LIKE.matcher(clean).replaceAll("sk-***");
        return clean.length() > MAX_MESSAGE ? clean.substring(0, MAX_MESSAGE) : clean;
    }
}
