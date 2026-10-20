package com.ggec.earbuds.domain.ai.model;

import java.util.Objects;

/**
 * 一条助手消息为什么没有正常结束。随消息保存，重新打开对话时仍能看到原因。
 */
public record AiMessageFailure(AiFailureKind kind, String detail) {

    public AiMessageFailure {
        Objects.requireNonNull(kind, "kind");
        detail = detail == null ? "" : detail;
    }
}
