package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;

/**
 * 麦克风采集。端口只描述"拿到 PCM16 帧和当前电平"，不描述用哪个 API 采集。
 *
 * <p><b>为什么电平单独给出来</b>：通话页的主角是那圈光晕，它必须跟着真实音量动。
 * 让界面自己去读 AudioRecord，绑定和权限就会散在两层里；放在端口上，页面只消费一个 0..1 的数。</p>
 *
 * <p><b>为什么有"上行闸门"而不是只有静音</b>：静音是用户意图，闸门是链路的双工策略。
 * 半双工通话里，助手发声期间必须停上行——否则扬声器的声音会被麦克风收回去，
 * 服务端 VAD 判成"用户开口"，助手就会自问自答（实机已验证：一句输入连答 6 轮）。
 * 闸门关掉时采集仍在跑、电平仍在算，只是不再把 PCM 送上去。</p>
 */
public interface AiAudioCapture {

    /**
     * @param sampleRateHz 16k：实时链路要求 PCM16 16k
     */
    void start(int sampleRateHz, @NonNull Listener listener);

    /** 停止采集并释放设备。幂等。 */
    void stop();

    /**
     * 上行闸门。关掉时 {@link Listener#onFrame} 仍会回调（电平可用），但 PCM 为空数组。
     * 幂等，可从任意线程调用。
     */
    void setUplinkEnabled(boolean enabled);

    /**
     * 设备侧回声消除是否真的生效。
     *
     * <p>必须在 {@link #start} 之后调用：只有真正打开了 AudioRecord、挂上了 AEC，
     * 这个值才有意义。装配根先用保守的半双工开局，拿到 true 再升级为全双工。</p>
     */
    default boolean echoCancellationActive() {
        return false;
    }

    interface Listener {

        /**
         * @param pcm16 一小段 16bit 单声道小端 PCM；闸门关闭时为空数组
         * @param level 0..1 的 RMS 电平，已做对数压缩，直接可以喂给动画
         */
        void onFrame(@NonNull byte[] pcm16, float level);

        void onFailure(@NonNull AiFailureKind kind, @NonNull String detail);
    }
}
