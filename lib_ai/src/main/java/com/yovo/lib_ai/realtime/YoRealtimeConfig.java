package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.platform.YoServiceTarget;

/**
 * 一次全双工语音通话的连接参数。
 *
 * <p>{@code baseUrl} / {@code apiKey} / {@code model} 来自用户已经配好的平台，
 * 和聊天、转写用的是同一份凭据——OMNI 模式不额外要用户填一次密钥。</p>
 *
 * <p><b>两个采样率不出现在协议里。</b>Realtime 的 {@code pcm16} 只有一个档位，
 * 线上没有「采样率」字段。这里留这两个值，是给采集端和播放端对齐用的：
 * {@code inputSampleRateHz} 交给 {@code AudioRecord}，{@code outputSampleRateHz}
 * 交给 {@code AudioTrack}。要是上层配错，声音会变调——所以两个值都必须正数。</p>
 */
public final class YoRealtimeConfig {

    /** 采集默认 16kHz：全双工语音里 16k 已经够 ASR 用，带宽和上行压力都小一半。 */
    public static final int DEFAULT_INPUT_SAMPLE_RATE_HZ = 16_000;

    /** 回答音频默认 24kHz：这是 Realtime 下行 {@code pcm16} 的原生档位，不重采样。 */
    public static final int DEFAULT_OUTPUT_SAMPLE_RATE_HZ = 24_000;

    @NonNull
    private final String baseUrl;
    @NonNull
    private final String apiKey;
    @NonNull
    private final String model;
    @Nullable
    private final String voice;
    @Nullable
    private final String instructions;
    private final int inputSampleRateHz;
    private final int outputSampleRateHz;

    private YoRealtimeConfig(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull String model,
            @Nullable String voice,
            @Nullable String instructions,
            int inputSampleRateHz,
            int outputSampleRateHz) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.voice = voice;
        this.instructions = instructions;
        this.inputSampleRateHz = inputSampleRateHz;
        this.outputSampleRateHz = outputSampleRateHz;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 归一化后的基址，不含尾部斜杠，协议前缀为 http(s)。
     */
    @NonNull
    public String baseUrl() {
        return baseUrl;
    }

    @NonNull
    public String apiKey() {
        return apiKey;
    }

    @NonNull
    public String model() {
        return model;
    }

    /**
     * 下行音色；为空表示用服务端默认音色。
     */
    @Nullable
    public String voice() {
        return voice;
    }

    /**
     * 系统提示词；为空表示不下发（沿用平台侧默认）。
     */
    @Nullable
    public String instructions() {
        return instructions;
    }

    public int inputSampleRateHz() {
        return inputSampleRateHz;
    }

    public int outputSampleRateHz() {
        return outputSampleRateHz;
    }

    /**
     * 故意不带 {@code apiKey}：这个对象很容易被打进日志。
     */
    @NonNull
    @Override
    public String toString() {
        return "YoRealtimeConfig{baseUrl=" + baseUrl
                + ", model=" + model
                + ", apiKey=***"
                + ", voice=" + voice
                + ", inputSampleRateHz=" + inputSampleRateHz
                + ", outputSampleRateHz=" + outputSampleRateHz
                + '}';
    }

    /**
     * 构造器。三个必填项在 {@link #build()} 里校验，错误尽早暴露在建立连接之前，
     * 而不是等用户在通话页看着「连接中」转圈。
     */
    public static final class Builder {

        private String baseUrl;
        private String apiKey;
        private String model;
        private String voice;
        private String instructions;
        private int inputSampleRateHz = DEFAULT_INPUT_SAMPLE_RATE_HZ;
        private int outputSampleRateHz = DEFAULT_OUTPUT_SAMPLE_RATE_HZ;

        private Builder() {
        }

        /**
         * 平台基址，例如 {@code https://api.openai.com}。带不带尾部斜杠都行。
         */
        @NonNull
        public Builder baseUrl(@NonNull String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        @NonNull
        public Builder apiKey(@NonNull String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * 全双工模型名，例如 {@code gpt-4o-realtime-preview}。
         */
        @NonNull
        public Builder model(@NonNull String model) {
            this.model = model;
            return this;
        }

        @NonNull
        public Builder voice(@Nullable String voice) {
            this.voice = voice;
            return this;
        }

        @NonNull
        public Builder instructions(@Nullable String instructions) {
            this.instructions = instructions;
            return this;
        }

        @NonNull
        public Builder inputSampleRateHz(int inputSampleRateHz) {
            this.inputSampleRateHz = inputSampleRateHz;
            return this;
        }

        @NonNull
        public Builder outputSampleRateHz(int outputSampleRateHz) {
            this.outputSampleRateHz = outputSampleRateHz;
            return this;
        }

        @NonNull
        public YoRealtimeConfig build() {
            // 三个必填项统一抛 IllegalArgumentException，别让「少了 baseUrl」是 NPE、
            // 「少了 apiKey」是 IAE——上层只写了一个 catch。
            String normalizedBaseUrl = YoServiceTarget.normalizeBaseUrl(requireText(baseUrl, "baseUrl"));
            String key = requireText(apiKey, "apiKey");
            String modelName = requireText(model, "model");
            if (inputSampleRateHz <= 0) {
                throw new IllegalArgumentException("inputSampleRateHz must be positive");
            }
            if (outputSampleRateHz <= 0) {
                throw new IllegalArgumentException("outputSampleRateHz must be positive");
            }
            return new YoRealtimeConfig(
                    normalizedBaseUrl,
                    key,
                    modelName,
                    blankToNull(voice),
                    blankToNull(instructions),
                    inputSampleRateHz,
                    outputSampleRateHz);
        }

        @NonNull
        private static String requireText(@Nullable String value, @NonNull String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value.trim();
        }

        @Nullable
        private static String blankToNull(@Nullable String value) {
            // 空串音色会被服务端当成合法音色名，然后整条会话建不起来。这里提前抹平。
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
