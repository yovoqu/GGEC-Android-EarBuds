package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

/**
 * 实时语音链路。一条长连接同时承担上行音频、下行音频和文本增量。
 *
 * <p>它不是"识别接口"的一种实现：转写、回答、发声都在模型服务端完成，
 * 客户端只负责送采集到的音频、播回传的音频、把文字增量交给界面。</p>
 */
public interface AiRealtimeLink {

    /** 建立连接。结果走 {@link Listener#onOutput}。 */
    void open(@NonNull Config config, @NonNull Listener listener);

    /** 送一段音频，或打断当前回答。 */
    void send(@NonNull AiRealtimeInput input);

    /**
     * 上行线格式要求的采样率。
     *
     * <p>这是<b>套接字上</b>的速率，不是麦克风的采集速率。两家默认值不同：
     * 通义 Omni-Realtime 默认 16 kHz，OpenAI 的 pcm16 按 24 kHz 解释。
     * 会话据此决定要不要重采样——发错速率听起来就是音调变低、语速变慢。</p>
     */
    int inputWireSampleRateHz();

    /**
     * 把服务端对话史里"用户没听到的那一段"截掉。
     *
     * <p>WebSocket 下播放由客户端管，服务端不知道用户听到哪；不截断的话，
     * 对话史里留着后半句，下一轮会接着那半句答（OpenAI 与通义都要求这一步）。</p>
     *
     * @param audioEndMs 用户实际听到的毫秒数
     */
    void truncate(@NonNull String itemId, int contentIndex, int audioEndMs);

    /** 关闭连接并释放。幂等。 */
    void close();

    /**
     * 连接参数用 baseUrl 而不是拼好的 wss 地址：wss 的拼法（路径、query、模型名转义）
     * 属于协议细节，留在 lib_ai 里；领域只说"连这个平台的这个模型"。
     *
     * @param bargeIn 助手发声时是否接受用户插话。半双工必须为 false：
     *                否则服务端 VAD 会把回声判成插话，助手会自问自答。
     */
    record Config(@NonNull String baseUrl, @NonNull String apiKey, @NonNull String model,
                  boolean bargeIn) {
    }

    interface Listener {
        void onOutput(@NonNull AiRealtimeOutput output);
    }
}
