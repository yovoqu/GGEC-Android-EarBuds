package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiRealtimeDialect;

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

    /** 关闭连接并释放。幂等。 */
    void close();

    /**
     * 连接参数用 baseUrl 而不是拼好的 wss 地址：wss 的拼法（路径、query、模型名转义）
     * 属于协议细节，留在 lib_ai 里；领域只说"连这个平台的这个模型"。
     */
    record Config(@NonNull String baseUrl, @NonNull String apiKey, @NonNull String model,
                  int inputSampleRateHz, int outputSampleRateHz,
                  @Nullable AiRealtimeDialect dialect) {

        /** 没指定方言时由实现按厂商默认线路连接。ChatGPT 的选择走六参构造。 */
        public Config(@NonNull String baseUrl, @NonNull String apiKey, @NonNull String model,
                      int inputSampleRateHz, int outputSampleRateHz) {
            this(baseUrl, apiKey, model, inputSampleRateHz, outputSampleRateHz, null);
        }
    }

    interface Listener {
        void onOutput(@NonNull AiRealtimeOutput output);
    }
}
