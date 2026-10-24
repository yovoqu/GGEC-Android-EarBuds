package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailure;

/**
 * 全双工实时链路（OMNI 模式）。一条长连接同时承担上行音频、下行音频和文本增量。
 *
 * <p>为什么把它和 {@link AiSpeechRecognizer}/{@link AiSpeechSynthesizer} 分成两套：
 * 它们的时序根本不同——级联是"说完一整段才开始识别"，实时是"边说边收"，
 * 中间还夹着"用户插话要打断正在生成的回答"。硬塞进一个接口只会让两边都别扭。</p>
 */
public interface AiRealtimeLink {

    /** 建立连接。回调里的音频是 24k PCM16，文本是增量。 */
    void open(@NonNull Config config, @NonNull Listener listener);

    /** 追加一段采集到的 16k PCM16。实现负责重采样与 base64。 */
    void appendAudio(@NonNull byte[] pcm16);

    /** 取消正在生成的回答（用户插话）。连接保持。 */
    void cancelResponse();

    /** 关闭连接并释放。幂等。 */
    void close();

    /**
     * 连接参数用 baseUrl 而不是拼好的 wss 地址：wss 的拼法（路径、query、模型名转义）
     * 属于协议细节，留在 lib_ai 里；领域只说"连这个平台的这个模型"。
     */
    record Config(@NonNull String baseUrl, @NonNull String apiKey, @NonNull String model,
                  int inputSampleRateHz, int outputSampleRateHz) {
    }

    interface Listener {
        void onOpened();

        /** 助手的文字增量：页面据此逐字上屏。 */
        void onAssistantText(@NonNull String delta);

        /** 助手的音频块。 */
        void onAssistantAudio(@NonNull byte[] pcm16);

        /** 服务端判定用户开口了；页面要立刻停掉正在播的回答。 */
        void onUserSpeechStarted();

        void onUserSpeechStopped();

        void onResponseDone();

        void onFailure(@NonNull AiFailure failure);
    }
}
