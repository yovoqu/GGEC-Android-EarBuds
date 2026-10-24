package com.ggec.earbuds.domain.ai.model;

public enum AiMessageStatus {
    COMPLETE,
    STREAMING,
    STOPPED,
    ERROR,
    /** 生成途中进程被杀，重开后显示"未完成"，可重新生成（CHAT-15）。 */
    INTERRUPTED;

    public static AiMessageStatus fromName(String name) {
        for (AiMessageStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return COMPLETE;
    }
}
