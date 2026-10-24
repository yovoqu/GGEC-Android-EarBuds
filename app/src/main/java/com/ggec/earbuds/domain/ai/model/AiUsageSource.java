package com.ggec.earbuds.domain.ai.model;

/** 一条用量记录的来源。旧记录没有这个字段，按 {@link #CHAT} 计。 */
public enum AiUsageSource {
    CHAT,
    CALL,
    CONNECTION_TEST,
    SPEED_TEST
}
