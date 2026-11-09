package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;

import java.util.Objects;

/**
 * 纯音频输入源端口（Audio Input Source）。
 *
 * <p>单一职责：只负责按源自身硬件/协议原生能力产生 PCM16 单声道小端音频帧并标注其真实采样率
 * {@code sampleRateHz}，通过 {@link Listener#onPcm(byte[], int)} 推送给上行管线。
 * 完全不感知网络协议要求的上行线速率、重采样、上行闸门静音状态与声波电平 UI，
 * 也不向调用方泄露任何底层平台或蓝牙传输实现。</p>
 */
public interface AiAudioSource {

    /** 输入源硬件/传输形态分类。 */
    enum Kind {
        BUILTIN_MIC,
        BLUETOOTH_SCO,
        BLUETOOTH_BLE,
        WIRED_HEADSET,
        USB_AUDIO,
        EXTERNAL_PCM
    }

    /**
     * 音频输入源不可变描述信息。
     *
     * @param id                    全局唯一且在同一会话内稳定的源标识
     * @param kind                  输入源分类
     * @param label                 展示名称（由平台层从系统设备名或蓝牙配置解析）
     * @param preferredSampleRateHz 源偏好的原生采集采样率（Hz，必须为正整数）
     */
    record Descriptor(
            @NonNull String id,
            @NonNull Kind kind,
            @NonNull String label,
            int preferredSampleRateHz) {

        public Descriptor {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(label, "label");
            if (preferredSampleRateHz <= 0) {
                throw new IllegalArgumentException("preferredSampleRateHz must be positive");
            }
        }
    }

    /** 返回当前音频源描述信息。 */
    @NonNull
    Descriptor descriptor();

    /**
     * 启动音频源生产。
     *
     * <p>实现方应在自身内部采集线程或外部推流回调中产出 PCM16 帧，并在 {@link #stop()}
     * 后保证幂等停止且不再向 {@code listener} 投递新帧。</p>
     */
    void start(@NonNull Listener listener);

    /** 停止生产并释放占用资源。必须幂等。 */
    void stop();

    interface Listener {
        /**
         * 产出一帧 PCM16 单声道小端数据。
         *
         * @param pcm16        原始 PCM 字节块（不允许为 null；偶数字节长度为佳）
         * @param sampleRateHz 这一帧真实的硬件/外部流采样率（Hz）
         */
        void onPcm(@NonNull byte[] pcm16, int sampleRateHz);

        /**
         * 输入源本身发生致命故障（例如麦克风不可用、权限失效、蓝牙链路异常断开）。
         */
        void onFailure(@NonNull AiFailureKind kind, @NonNull String detail);
    }
}
