package com.ggec.earbuds.domain.ai.model;

import java.util.Objects;

/**
 * 用户输入不满足规则。界面按 {@link #error()} 选择提示语，不显示异常消息。
 */
public final class AiValidationException extends RuntimeException {

    private final AiValidationError error;

    public AiValidationException(AiValidationError error) {
        super(Objects.requireNonNull(error, "error").name());
        this.error = error;
    }

    public AiValidationError error() {
        return error;
    }
}
