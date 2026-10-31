package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ChatGPT Realtime 的线路方言。SDK 定义了能说哪些，这里只保留 APP 要存、要选的那两项。
 * 通义不在此阶段。
 */
public enum AiRealtimeDialect {

    BETA("openai_beta"),
    GA("openai_ga");

    private final String id;

    AiRealtimeDialect(String id) {
        this.id = id;
    }

    /** 写进设置文档的稳定标识。 */
    @NonNull
    public String id() {
        return id;
    }

    @NonNull
    public static AiRealtimeDialect defaultDialect() {
        return BETA;
    }

    @Nullable
    public static AiRealtimeDialect fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (AiRealtimeDialect dialect : values()) {
            if (dialect.id.equalsIgnoreCase(id) || dialect.name().equalsIgnoreCase(id)) {
                return dialect;
            }
        }
        return null;
    }
}
