package com.ggec.earbuds.domain.ai.model;

import java.util.Objects;

/**
 * 网关把底层失败折成领域失败。{@link #getMessage()} 是平台给出的技术说明，可能为空，不含密钥。
 */
public final class AiFailure extends RuntimeException {

    private final AiFailureKind kind;

    public AiFailure(AiFailureKind kind, String message) {
        this(kind, message, null);
    }

    public AiFailure(AiFailureKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public AiFailureKind kind() {
        return kind;
    }
}
