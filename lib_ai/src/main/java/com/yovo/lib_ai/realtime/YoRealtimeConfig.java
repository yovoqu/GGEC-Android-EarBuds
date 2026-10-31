package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.platform.YoServiceTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次全双工语音通话的连接参数。
 *
 * <p>{@code baseUrl} / {@code apiKey} / {@code model} 来自用户已经配好的平台，
 * 和聊天、转写用的是同一份凭据。</p>
 *
 * <p><b>两套采样率不要混用。</b>{@code inputSampleRateHz} 是麦克风那一侧（交给 {@code AudioRecord}），
 * {@link #inputWireFormat()} 才是套接字上的字节。app 经常 16kHz 采集、升到 24kHz 再上行，
 * 所以线格式默认 24kHz PCM，不跟着麦克风的 16kHz 走。{@code outputSampleRateHz} 同时是播放端
 * 和默认下行线格式：下行不再重采样。</p>
 */
public final class YoRealtimeConfig {

    /** 采集默认 16kHz：全双工语音里 16k 已经够 ASR 用，带宽和上行压力都小一半。 */
    public static final int DEFAULT_INPUT_SAMPLE_RATE_HZ = 16_000;

    /** 线上 PCM 与下行播放的默认档位。OpenAI 的 {@code pcm16} 按这个速率解释字节。 */
    public static final int DEFAULT_OUTPUT_SAMPLE_RATE_HZ = 24_000;

    @NonNull
    private final String baseUrl;
    @NonNull
    private final String apiKey;
    @NonNull
    private final String model;
    @NonNull
    private final YoRealtimeDialect dialect;
    @Nullable
    private final String voice;
    @Nullable
    private final String instructions;
    private final int inputSampleRateHz;
    private final int outputSampleRateHz;
    @NonNull
    private final YoRealtimeAudioFormat inputWireFormat;
    @NonNull
    private final YoRealtimeAudioFormat outputWireFormat;
    @Nullable
    private final YoRealtimeTurnDetection turnDetection;
    @Nullable
    private final YoRealtimeTranscription transcription;
    @Nullable
    private final YoRealtimeNoiseReduction noiseReduction;
    @NonNull
    private final List<YoRealtimeTool> tools;
    @Nullable
    private final List<YoRealtimeOutputModality> outputModalities;

    private YoRealtimeConfig(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull String model,
            @NonNull YoRealtimeDialect dialect,
            @Nullable String voice,
            @Nullable String instructions,
            int inputSampleRateHz,
            int outputSampleRateHz,
            @NonNull YoRealtimeAudioFormat inputWireFormat,
            @NonNull YoRealtimeAudioFormat outputWireFormat,
            @Nullable YoRealtimeTurnDetection turnDetection,
            @Nullable YoRealtimeTranscription transcription,
            @Nullable YoRealtimeNoiseReduction noiseReduction,
            @NonNull List<YoRealtimeTool> tools,
            @Nullable List<YoRealtimeOutputModality> outputModalities) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dialect = dialect;
        this.voice = voice;
        this.instructions = instructions;
        this.inputSampleRateHz = inputSampleRateHz;
        this.outputSampleRateHz = outputSampleRateHz;
        this.inputWireFormat = inputWireFormat;
        this.outputWireFormat = outputWireFormat;
        this.turnDetection = turnDetection;
        this.transcription = transcription;
        this.noiseReduction = noiseReduction;
        this.tools = tools;
        this.outputModalities = outputModalities;
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

    @NonNull
    public YoRealtimeDialect dialect() {
        return dialect;
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

    /** 麦克风采样率，不写进协议。 */
    public int inputSampleRateHz() {
        return inputSampleRateHz;
    }

    /** 播放采样率。没单独指定下行线格式时，线格式跟这个数走。 */
    public int outputSampleRateHz() {
        return outputSampleRateHz;
    }

    /** 上行字节的格式。默认 24kHz PCM16，和 beta 线上隐含的档位一致。 */
    @NonNull
    public YoRealtimeAudioFormat inputWireFormat() {
        return inputWireFormat;
    }

    @NonNull
    public YoRealtimeAudioFormat outputWireFormat() {
        return outputWireFormat;
    }

    /** 为空表示用方言默认的服务端 VAD。 */
    @Nullable
    public YoRealtimeTurnDetection turnDetection() {
        return turnDetection;
    }

    @Nullable
    public YoRealtimeTranscription transcription() {
        return transcription;
    }

    @Nullable
    public YoRealtimeNoiseReduction noiseReduction() {
        return noiseReduction;
    }

    @NonNull
    public List<YoRealtimeTool> tools() {
        return tools;
    }

    /**
     * 为空表示用方言默认：GA 只出音频，通义文字加音频，beta 不下发、沿用服务端。
     */
    @Nullable
    public List<YoRealtimeOutputModality> outputModalities() {
        return outputModalities;
    }

    /**
     * 故意不带 {@code apiKey}：这个对象很容易被打进日志。
     */
    @NonNull
    @Override
    public String toString() {
        return "YoRealtimeConfig{baseUrl=" + baseUrl
                + ", model=" + model
                + ", dialect=" + dialect
                + ", apiKey=***"
                + ", voice=" + voice
                + ", inputSampleRateHz=" + inputSampleRateHz
                + ", outputSampleRateHz=" + outputSampleRateHz
                + ", inputWire=" + inputWireFormat
                + ", outputWire=" + outputWireFormat
                + ", tools=" + tools.size()
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
        private YoRealtimeDialect dialect = YoRealtimeDialect.OPENAI_BETA;
        private String voice;
        private String instructions;
        private int inputSampleRateHz = DEFAULT_INPUT_SAMPLE_RATE_HZ;
        private int outputSampleRateHz = DEFAULT_OUTPUT_SAMPLE_RATE_HZ;
        private YoRealtimeAudioFormat inputWireFormat =
                YoRealtimeAudioFormat.pcm16(DEFAULT_OUTPUT_SAMPLE_RATE_HZ);
        private YoRealtimeAudioFormat outputWireFormat;
        private YoRealtimeTurnDetection turnDetection;
        private YoRealtimeTranscription transcription;
        private YoRealtimeNoiseReduction noiseReduction;
        private final List<YoRealtimeTool> tools = new ArrayList<>();
        private List<YoRealtimeOutputModality> outputModalities;

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
        public Builder dialect(@NonNull YoRealtimeDialect dialect) {
            this.dialect = dialect;
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

        /** 上行字节的格式。不调用则是 24kHz PCM16，与麦克风采样率无关。 */
        @NonNull
        public Builder inputWireFormat(@NonNull YoRealtimeAudioFormat inputWireFormat) {
            this.inputWireFormat = inputWireFormat;
            return this;
        }

        /** 下行字节的格式。不调用则跟 {@link #outputSampleRateHz} 走，编码为 PCM16。 */
        @NonNull
        public Builder outputWireFormat(@NonNull YoRealtimeAudioFormat outputWireFormat) {
            this.outputWireFormat = outputWireFormat;
            return this;
        }

        @NonNull
        public Builder turnDetection(@Nullable YoRealtimeTurnDetection turnDetection) {
            this.turnDetection = turnDetection;
            return this;
        }

        @NonNull
        public Builder transcription(@Nullable YoRealtimeTranscription transcription) {
            this.transcription = transcription;
            return this;
        }

        @NonNull
        public Builder noiseReduction(@Nullable YoRealtimeNoiseReduction noiseReduction) {
            this.noiseReduction = noiseReduction;
            return this;
        }

        @NonNull
        public Builder tools(@NonNull List<YoRealtimeTool> tools) {
            this.tools.clear();
            this.tools.addAll(tools);
            return this;
        }

        @NonNull
        public Builder outputModalities(@Nullable List<YoRealtimeOutputModality> outputModalities) {
            if (outputModalities == null || outputModalities.isEmpty()) {
                this.outputModalities = null;
            } else {
                this.outputModalities = List.copyOf(outputModalities);
            }
            return this;
        }

        @NonNull
        public YoRealtimeConfig build() {
            String normalizedBaseUrl = YoServiceTarget.normalizeBaseUrl(requireText(baseUrl, "baseUrl"));
            String key = requireText(apiKey, "apiKey");
            String modelName = requireText(model, "model");
            if (dialect == null) {
                throw new IllegalArgumentException("dialect is required");
            }
            if (inputSampleRateHz <= 0) {
                throw new IllegalArgumentException("inputSampleRateHz must be positive");
            }
            if (outputSampleRateHz <= 0) {
                throw new IllegalArgumentException("outputSampleRateHz must be positive");
            }
            if (inputWireFormat == null) {
                throw new IllegalArgumentException("inputWireFormat is required");
            }
            YoRealtimeAudioFormat outputWire = outputWireFormat == null
                    ? YoRealtimeAudioFormat.pcm16(outputSampleRateHz)
                    : outputWireFormat;
            return new YoRealtimeConfig(
                    normalizedBaseUrl,
                    key,
                    modelName,
                    dialect,
                    blankToNull(voice),
                    blankToNull(instructions),
                    inputSampleRateHz,
                    outputSampleRateHz,
                    inputWireFormat,
                    outputWire,
                    turnDetection,
                    transcription,
                    noiseReduction,
                    List.copyOf(tools),
                    outputModalities == null ? null : List.copyOf(outputModalities));
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
