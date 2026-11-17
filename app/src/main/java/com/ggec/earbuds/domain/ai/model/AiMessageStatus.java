package com.ggec.earbuds.domain.ai.model;

import java.util.Optional;

/**
 * 一条消息的完成状态。持久化值是枚举名；未知值返回空，由解码方按文档损坏处理：
 * 把流式中途被杀的消息读成"已完成"会让界面永远等不到下一段文本。
 */
public enum AiMessageStatus {
    COMPLETE,
    STREAMING,
    STOPPED,
    ERROR;

    public static Optional<AiMessageStatus> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AiMessageStatus status : values()) {
            if (status.name().equals(name)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
