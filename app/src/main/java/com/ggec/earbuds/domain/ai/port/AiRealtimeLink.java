package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

/**
 * 实时语音链路。一条长连接同时承担上行音频、控制命令、下行音频与文本增量。
 *
 * <p>上行与下行协商后的线速率通过 {@link AiRealtimeOutput.Opened} 向上游公布；
 * 音频块、打断、截断与清空统一走 {@link #send(AiRealtimeInput)} 单入口。</p>
 */
public interface AiRealtimeLink {

    /** 建立连接。结果走 {@link Listener#onOutput}。 */
    void open(@NonNull Config config, @NonNull Listener listener);

    /** 送一段音频，或发送取消/截断/清空控制指令。 */
    void send(@NonNull AiRealtimeInput input);

    /** 关闭连接并释放。幂等。 */
    void close();

    /**
     * 连接参数只表达目标平台端点、密钥与模型；方言细节留在基础设施适配器内部解析。
     */
    record Config(@NonNull String baseUrl, @NonNull String apiKey, @NonNull String model) {
    }

    interface Listener {
        void onOutput(@NonNull AiRealtimeOutput output);
    }
}
