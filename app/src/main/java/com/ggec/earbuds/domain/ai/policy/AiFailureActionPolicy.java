package com.ggec.earbuds.domain.ai.policy;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;

/**
 * 失败卡给哪些动作（CHAT-10）：临时故障给"重试"，配置问题给"去设置"，说不清的两个都给。
 */
public final class AiFailureActionPolicy {

    public record Actions(boolean retry, boolean settings) {
    }

    private AiFailureActionPolicy() {
    }

    public static Actions of(AiFailureKind kind) {
        return switch (kind) {
            case NETWORK, TIMEOUT, RATE_LIMITED, SERVER -> new Actions(true, false);
            case AUTHENTICATION, PERMISSION, NOT_FOUND, BAD_REQUEST, QUOTA, PROTOCOL, NO_PLATFORM, NO_MODEL,
                    UNSUPPORTED_CAPABILITY -> new Actions(false, true);
            case CANCELLED, STORAGE, UNKNOWN -> new Actions(true, true);
        };
    }
}
