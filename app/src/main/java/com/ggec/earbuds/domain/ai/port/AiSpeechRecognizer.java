package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.voice.AiVoiceException;

/**
 * 语音转写（级联模式的第一段）。实现走 Whisper 兼容的 `/audio/transcriptions`。
 *
 * <p>为什么入参是原始 PCM 而不是文件：采集端已经是 16k/16bit/单声道，
 * 让实现层负责包 wav 头，避免每个调用方各自拼一遍音频容器。</p>
 */
public interface AiSpeechRecognizer {

    /**
     * @param pcm16        一段完整的话（已经在静音处切好）
     * @param sampleRateHz 采样率，与采集端一致
     * @param model        平台侧的转写模型 id；为空表示用平台默认
     * @return 识别出的文本；静音或识别为空时返回空串（调用方据此跳过这一轮）
     */
    @NonNull
    String transcribe(@NonNull byte[] pcm16, int sampleRateHz, @Nullable String model)
            throws AiVoiceException;
}
