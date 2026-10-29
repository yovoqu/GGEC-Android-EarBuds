package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;

/**
 * 麦克风采集。端口只描述"拿到 PCM16 帧和当前电平"，不描述用哪个 API 采集。
 *
 * <p>为什么要把电平单独给出来：通话页的主角是那圈光晕，它必须跟着真实音量动。
 * 让界面自己去读 AudioRecord，绑定和权限就会散在两层里；放在端口上，页面只消费一个 0..1 的数。</p>
 */
public interface AiAudioCapture {

    /**
     * @param sampleRateHz 16k：实时链路要求 PCM16 16k
     */
    void start(int sampleRateHz, @NonNull Listener listener);

    /** 停止采集并释放设备。幂等。 */
    void stop();

    /**
     * 只停上行，不释放设备：静音期间仍然要拿到电平（用户需要看到"我在说话但没发出去"）。
     */
    void setMuted(boolean muted);

    interface Listener {

        /**
         * @param pcm16 一小段 16bit 单声道小端 PCM
         * @param level 0..1 的 RMS 电平，已做对数压缩，直接可以喂给动画
         */
        void onFrame(@NonNull byte[] pcm16, float level);

        void onFailure(@NonNull AiFailureKind kind, @NonNull String detail);
    }
}
