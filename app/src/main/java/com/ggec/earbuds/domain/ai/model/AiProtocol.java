package com.ggec.earbuds.domain.ai.model;

import java.util.Optional;

/**
 * 平台使用的对话协议。持久化值是 {@link #id()}，解析不出来就是 {@link Optional#empty()}：
 * 未知协议不能被折成某一个已知协议，那会让下次请求发到错误的端点上。
 */
public enum AiProtocol {
    CHAT_COMPLETIONS("chat_completions"),
    RESPONSES("responses"),
    MESSAGES("messages");

    private final String id;

    AiProtocol(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<AiProtocol> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (AiProtocol protocol : values()) {
            if (protocol.id.equals(id)) {
                return Optional.of(protocol);
            }
        }
        return Optional.empty();
    }
}
