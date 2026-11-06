package com.ggec.earbuds.domain.ai.voice;

/**
 * 通话的双工通信方式。
 *
 * <p>双工方式是通话状态机与交互的核心领域概念，定义了双方在时间线上的通信规则：
 * <ul>
 *   <li>{@link #FULL_DUPLEX}（全双工 · 自由流式模式）：双向并发通道，麦克风持续上行，用户可随时插话打断（Barge-in）；</li>
 *   <li>{@link #HALF_DUPLEX}（半双工 · 轮流交谈模式）：单向交替对讲，助手播音期间上行受控静默，播音完毕交回话语权。</li>
 * </ul>
 * </p>
 */
public enum VoiceDuplex {

    /**
     * 半双工：单向交替轮流发言。助手发声期间闭合上行闸门，播报清晰连贯，不受环境人声打扰。
     */
    HALF_DUPLEX,

    /**
     * 全双工：双向并发自由交流。助手发声期间保持上行开启，支持随时插话打断。
     */
    FULL_DUPLEX;

    /** 助手发声期间是否继续保持麦克风上行流。 */
    public boolean streamsWhileAssistantSpeaks() {
        return this == FULL_DUPLEX;
    }
}
