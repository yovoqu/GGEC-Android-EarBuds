package com.ggec.earbuds.domain.ai.model;

import java.util.Optional;

/**
 * 消息的发送方。持久化值是枚举名；解析不出来就是空的，不能默认成某一边——
 * 把助手消息读成用户消息会让重放上下文时把答案当成提问。
 */
public enum AiRole {
    USER,
    ASSISTANT;

    public static Optional<AiRole> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AiRole role : values()) {
            if (role.name().equals(name)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
