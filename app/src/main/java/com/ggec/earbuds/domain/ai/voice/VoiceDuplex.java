package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

/**
 * 通话的双工通信方式（纯粹的模式标识与持久化键值，不包含具体调度实现）。
 *
 * <p>运行期行为已解耦至 {@link VoiceDuplexStrategy}：
 * <ul>
 *   <li>{@link #FULL_DUPLEX}（全双工 · 自由流式模式）：双向并发通道，麦克风持续上行，支持随时插话打断；</li>
 *   <li>{@link #HALF_DUPLEX}（半双工 · 轮流交谈模式）：单向交替对讲，助手发声期间闭合上行闸门。</li>
 * </ul>
 * </p>
 */
public enum VoiceDuplex {

    /**
     * 半双工：单向交替轮流发言。助手发声期间闭合上行闸门，播报清晰连贯，不受环境人声打扰。
     */
    HALF_DUPLEX("half_duplex"),

    /**
     * 全双工：双向并发自由交流。助手发声期间保持上行开启，支持随时插话打断。
     */
    FULL_DUPLEX("full_duplex");

    private final String id;

    VoiceDuplex(@NonNull String id) {
        this.id = id;
    }

    /** 持久化与跨层传递使用的稳定标识。 */
    @NonNull
    public String id() {
        return id;
    }

    /** 新装应用的缺省双工：半双工是安全起点，避免外放回声在首次使用时引发自触发。 */
    @NonNull
    public static VoiceDuplex defaultMode() {
        return HALF_DUPLEX;
    }

    /** 持久化值是 {@link #id()}；解析不出来就是空的，由解码方按文档损坏处理。 */
    public static Optional<VoiceDuplex> fromId(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (VoiceDuplex mode : values()) {
            if (mode.id.equals(id)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /** 返回该模式对应的独立运行期行为策略。 */
    @NonNull
    public VoiceDuplexStrategy strategy() {
        return VoiceDuplexStrategy.forMode(this);
    }
}
