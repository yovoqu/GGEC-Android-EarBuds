package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.speech.YoSynthesisRequest;
import com.yovo.lib_ai.speech.YoSynthesisResult;
import com.yovo.lib_ai.speech.YoSynthesisStreamListener;

/**
 * 文本合成语音（TTS）端口。
 *
 * <p>同时覆盖两种方言：OpenAI {@code POST /v1/audio/speech} 与通义百炼
 * {@code POST /api/v1/services/audio/tts/SpeechSynthesizer}。调用方按平台配置选择
 * {@link YoSynthesisRequest.Dialect}，本端口不猜平台。
 *
 * <p>线程约定：{@link #synthesize} 是阻塞调用，内部等满一次 HTTP 往返（长文本可能较久），
 * 不要在 Android 主线程直接调。{@link #stream} 也按同步方式驱动监听器，但允许
 * {@link YoAiCall#cancel()} 在音频片段之间尽早停下。
 */
public interface YoSynthesis {

    /**
     * 一次性合成，返回完整音频字节（OpenAI 二进制）或远端音频地址（Qwen {@code output.audio.url}）。
     *
     * @throws com.yovo.lib_ai.error.YoAiException 网络、超时、鉴权、限流、协议不兼容等失败
     * @throws IllegalArgumentException 必填字段缺失或参数越界（本地拒绝，不发网络）
     */
    @NonNull
    YoSynthesisResult synthesize(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoSynthesisRequest request);

    /**
     * 流式合成。OpenAI {@code stream_format=sse} 时按 {@code speech.audio.delta} 逐段回调，
     * 其余情况在拿到完整结果后一次性回调。监听器回调都在调用线程上顺序发生。
     */
    @NonNull
    YoAiCall stream(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoSynthesisRequest request,
            @NonNull YoSynthesisStreamListener listener);
}
