package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.error.YoAiException;

/**
 * 语音转写端口（OpenAI Whisper 兼容的 {@code POST /audio/transcriptions}）。
 *
 * <p>为什么 baseUrl 与 apiKey 按调用传入而不是构造时绑定：CASCADED 语音链路里，
 * 转写可能和对话用同一台平台，也可能单独配一台（{@code AiVoiceSettings.asrPlatformId}），
 * 一次通话里两者都可能切换；把凭据放在构造期会逼调用方为每次切换新建客户端实例。
 *
 * <p>线程约定：{@link #transcribe} 是阻塞调用，内部会等满一次完整的 HTTP 往返，
 * 不要在 Android 主线程直接调。实现需要可被复用（连接池复用），关闭由实现类的
 * {@code close()} 负责，而不是本接口。
 */
public interface YoSpeech {

    /**
     * 转写一段音频。
     *
     * @param baseUrl 平台基址，如 {@code https://api.openai.com}；已带 {@code /v1} 也可
     * @param apiKey  平台 API Key，走 {@code Authorization: Bearer}
     * @param request 音频与识别参数
     * @return 识别结果；静音合法地返回空文本
     * @throws YoAiException 网络、超时、鉴权、限流、协议不兼容等失败；
     *                       分类用 {@code YoAiErrors.kindOf(throwable)} 取，
     *                       调用方不解析状态码
     * @throws IllegalArgumentException 参数不合法（缺 Key、空音频、格式错误的 baseUrl）
     */
    @NonNull
    YoTranscription transcribe(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoTranscriptionRequest request);
}
