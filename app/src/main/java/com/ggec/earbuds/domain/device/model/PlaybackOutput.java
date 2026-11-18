package com.ggec.earbuds.domain.device.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 一个可播放的物理输出设备（播放源），例如耳机、手机扬声器、有线耳机、USB 音频。
 *
 * <p>这是纯领域模型：只描述「是哪一类设备、平台给它的稳定 id、以及平台上报的产品名」。
 * 面向用户的文案不在这里，由界面按 {@link Kind} 映射到字符串资源。</p>
 */
public record PlaybackOutput(
        @NonNull String id,
        @NonNull Kind kind,
        @NonNull String label) {

    public PlaybackOutput {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        label = label == null ? "" : label;
    }

    /** 物理输出设备类别：界面据此选图标与文案。 */
    public enum Kind {
        /** 蓝牙耳机 / TWS 耳塞（BLE、A2DP、SCO 都归此类）。 */
        EARBUDS,
        /** 手机外放扬声器。 */
        PHONE_SPEAKER,
        /** 手机听筒。 */
        EARPIECE,
        /** 有线耳机 / 头戴。 */
        WIRED,
        /** USB 音频设备。 */
        USB,
        /** 其他或无法识别的输出。 */
        OTHER
    }
}
