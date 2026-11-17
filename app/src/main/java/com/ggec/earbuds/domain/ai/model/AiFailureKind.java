package com.ggec.earbuds.domain.ai.model;

import java.util.Optional;

public enum AiFailureKind {
    NO_PLATFORM,
    NO_MODEL,
    /** 语音模型选好了，但这一家的 API Key 还没填。 */
    VOICE_NOT_CONFIGURED,
    AUTHENTICATION,
    PERMISSION,
    QUOTA,
    RATE_LIMITED,
    NOT_FOUND,
    BAD_REQUEST,
    /** 内容被安全策略过滤。 */
    CONTENT_FILTERED,
    /** 输入超过上下文窗口。 */
    CONTEXT_LENGTH,
    /** 参数/能力不被模型支持。 */
    UNSUPPORTED_PARAM,
    /** 服务端过载。 */
    OVERLOADED,
    SERVER,
    NETWORK,
    TIMEOUT,
    CANCELLED,
    /** 平台里没有具备该能力的模型（例如要求看图，但当前平台只支持文本）。 */
    UNSUPPORTED_CAPABILITY,
    PROTOCOL,
    /** 文档或密钥读不出来、写不进去。 */
    STORAGE,
    UNKNOWN;

    public boolean retryable() {
        return this == RATE_LIMITED || this == SERVER || this == OVERLOADED || this == NETWORK || this == TIMEOUT;
    }

    /** 持久化值是枚举名；解析不出来就是空的，由解码方按文档损坏处理，不折成 {@link #UNKNOWN}。 */
    public static Optional<AiFailureKind> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AiFailureKind kind : values()) {
            if (kind.name().equals(name)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
