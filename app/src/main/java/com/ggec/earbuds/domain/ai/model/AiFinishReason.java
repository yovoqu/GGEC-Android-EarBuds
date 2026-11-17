package com.ggec.earbuds.domain.ai.model;

import java.util.Optional;

/**
 * 模型这次为什么结束输出。持久化值是枚举名；未知值返回空，由解码方按文档损坏处理：
 * 把"被截断"读成 STOP 会把没说完的回答当成完整回答。
 */
public enum AiFinishReason {
    STOP,
    LENGTH,
    FILTERED,
    OTHER;

    public static Optional<AiFinishReason> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AiFinishReason reason : values()) {
            if (reason.name().equals(name)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
