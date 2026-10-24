package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;

/**
 * 语音合成（级联模式的最后一段）。
 *
 * <p>为什么是"念出来"而不是"返回字节"：系统 TTS 自己就负责播放，拿不到 PCM；
 * 平台 TTS 拿到音频也要有播放器。把播放留在实现里，两种来源就能共用同一个端口——
 * 调用方只说"把这句话念给用户听"，不关心声音是从哪来的。</p>
 */
public interface AiSpeechSynthesizer {

    /**
     * 阻塞直到念完或失败。调用方在后台线程调用。
     *
     * @param voice 音色名；为空表示平台或系统默认
     */
    void speak(@NonNull String text, @Nullable String voice, @NonNull Listener listener)
            throws com.ggec.earbuds.domain.ai.voice.AiVoiceException;

    /** 立刻停止朗读（用户插话）。幂等。 */
    void stop();

    interface Listener {
        /** 开始出声：页面据此从 THINKING 切到 SPEAKING。 */
        void onStarted();

        void onDone();

        void onFailure(@NonNull AiFailure failure);
    }
}
