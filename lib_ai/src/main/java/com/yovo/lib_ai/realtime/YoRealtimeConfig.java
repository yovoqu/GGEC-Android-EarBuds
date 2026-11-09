package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.platform.YoServiceTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次实时语音会话的纯连接与协议参数。
 *
 * <p>{@code baseUrl} / {@code apiKey} / {@code model} 来自用户配好的平台凭据；
 * 本配置只关心协议线上的格式与会话参数（{@link #inputWireFormat()} / {@link #outputWireFormat()}），
 * 不涉足麦克风采集、本地重采样、播放进度或自动打断策略。</p>
 */
public final class YoRealtimeConfig {

    /** OpenAI 实时链路的默认线速率（24kHz）。 */
    public static final int DEFAULT_WIRE_SAMPLE_RATE_HZ = 24_000;

    /** 通义 Omni-Realtime 的默认上行线速率（16kHz）。 */
    public static final int DEFAULT_QWEN_INPUT_SAMPLE_RATE_HZ = 16_000;

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
    @Nullable
    private final String translationTarget;

    private YoRealtimeConfig(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull String model,
            @NonNull YoRealtimeDialect dialect,
            @Nullable String voice,
            @Nullable String instructions,
            @NonNull YoRealtimeAudioFormat inputWireFormat,
            @NonNull YoRealtimeAudioFormat outputWireFormat,
            @Nullable YoRealtimeTurnDetection turnDetection,
            @Nullable YoRealtimeTranscription transcription,
            @Nullable YoRealtimeNoiseReduction noiseReduction,
            @NonNull List<YoRealtimeTool> tools,
            @Nullable List<YoRealtimeOutputModality> outputModalities,
            @Nullable String translationTarget) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dialect = dialect;
        this.voice = voice;
        this.instructions = instructions;
        this.inputWireFormat = inputWireFormat;
        this.outputWireFormat = outputWireFormat;
        this.turnDetection = turnDetection;
        this.transcription = transcription;
        this.noiseReduction = noiseReduction;
        this.tools = tools;
        this.outputModalities = outputModalities;
        this.translationTarget = translationTarget;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /** 归一化后的基址，不含尾部斜杠，协议前缀为 http(s)。 */
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

    /** 下行音色；为空表示用服务端默认音色。 */
    @Nullable
    public String voice() {
        return voice;
    }

    /** 系统提示词；为空表示不下发（沿用平台侧默认）。 */
    @Nullable
    public String instructions() {
        return instructions;
    }

    /** 上行套接字字节格式。默认按方言选择（通义 16k PCM16，OpenAI 24k PCM16）。 */
    @NonNull
    public YoRealtimeAudioFormat inputWireFormat() {
        return inputWireFormat;
    }

    /** 下行套接字字节格式。默认 24kHz PCM16。 */
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

    /** 为空表示用方言默认：GA 只出音频，通义文字加音频，beta 不下发、沿用服务端。 */
    @Nullable
    public List<YoRealtimeOutputModality> outputModalities() {
        return outputModalities;
    }

    /** 翻译目标语种（Qwen realtime 翻译模式）；为空表示不做翻译。 */
    @Nullable
    public String translationTarget() {
        return translationTarget;
    }

    /** 故意不带 {@code apiKey}：这个对象很容易被打进日志。 */
    @NonNull
    @Override
    public String toString() {
        return "YoRealtimeConfig{baseUrl=" + baseUrl
                + ", model=" + model
                + ", dialect=" + dialect
                + ", apiKey=***"
                + ", voice=" + voice
                + ", inputWire=" + inputWireFormat
                + ", outputWire=" + outputWireFormat
                + ", tools=" + tools.size()
                + ", translationTarget=" + translationTarget
                + '}';
    }

    /** 构造器：在建立连接前校验必填字段及方言一致性。 */
    public static final class Builder {

        private String baseUrl;
        private String apiKey;
        private String model;
        private YoRealtimeDialect dialect;
        private String voice;
        private String instructions;
        private YoRealtimeAudioFormat inputWireFormat;
        private YoRealtimeAudioFormat outputWireFormat;
        private YoRealtimeTurnDetection turnDetection;
        private YoRealtimeTranscription transcription;
        private YoRealtimeNoiseReduction noiseReduction;
        private final List<YoRealtimeTool> tools = new ArrayList<>();
        private List<YoRealtimeOutputModality> outputModalities;
        private String translationTarget;

        private Builder() {
        }

        /** 平台基址，例如 {@code https://api.openai.com}。 */
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

        /** 实时模型名，例如 {@code gpt-4o-realtime-preview}。 */
        @NonNull
        public Builder model(@NonNull String model) {
            this.model = model;
            return this;
        }

        @NonNull
        public Builder dialect(@Nullable YoRealtimeDialect dialect) {
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

        /** 上行线格式。未指定时按方言默认（QWEN=16kHz PCM16，其余=24kHz PCM16）。 */
        @NonNull
        public Builder inputWireFormat(@NonNull YoRealtimeAudioFormat inputWireFormat) {
            this.inputWireFormat = inputWireFormat;
            return this;
        }

        /** 下行线格式。未指定时默认 24kHz PCM16。 */
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

        /** 翻译目标语种；为空表示不做翻译。 */
        @NonNull
        public Builder translationTarget(@Nullable String translationTarget) {
            this.translationTarget = blankToNull(translationTarget);
            return this;
        }

        @NonNull
        public YoRealtimeConfig build() {
            String normalizedBaseUrl = YoServiceTarget.normalizeBaseUrl(requireText(baseUrl, "baseUrl"));
            String key = requireText(apiKey, "apiKey");
            String modelName = requireText(model, "model");
            YoRealtimeDialect resolvedDialect = dialect != null
                    ? dialect
                    : YoRealtimeDialect.infer(normalizedBaseUrl, modelName);
            YoRealtimeAudioFormat resolvedInputWire = inputWireFormat != null
                    ? inputWireFormat
                    : YoRealtimeAudioFormat.pcm16(resolvedDialect == YoRealtimeDialect.QWEN
                            ? DEFAULT_QWEN_INPUT_SAMPLE_RATE_HZ
                            : DEFAULT_WIRE_SAMPLE_RATE_HZ);
            YoRealtimeAudioFormat resolvedOutputWire = outputWireFormat != null
                    ? outputWireFormat
                    : YoRealtimeAudioFormat.pcm16(DEFAULT_WIRE_SAMPLE_RATE_HZ);
            String resolvedVoice = blankToNull(voice);
            if (resolvedVoice == null && resolvedDialect == YoRealtimeDialect.QWEN) {
                resolvedVoice = "Tina";
            }
            requireTurnDetectionMatchesDialect(resolvedDialect, turnDetection);
            return new YoRealtimeConfig(
                    normalizedBaseUrl,
                    key,
                    modelName,
                    resolvedDialect,
                    resolvedVoice,
                    blankToNull(instructions),
                    resolvedInputWire,
                    resolvedOutputWire,
                    turnDetection,
                    transcription,
                    noiseReduction,
                    List.copyOf(tools),
                    outputModalities == null ? null : List.copyOf(outputModalities),
                    translationTarget);
        }

        private static void requireTurnDetectionMatchesDialect(
                @NonNull YoRealtimeDialect dialect,
                @Nullable YoRealtimeTurnDetection turnDetection) {
            if (turnDetection == null || turnDetection instanceof YoRealtimeTurnDetection.Disabled) {
                return;
            }
            boolean qwenDialect = dialect == YoRealtimeDialect.QWEN;
            boolean qwenConfig = turnDetection instanceof YoRealtimeTurnDetection.Qwen;
            if (qwenDialect != qwenConfig) {
                throw new IllegalArgumentException("turnDetection variant "
                        + turnDetection.getClass().getSimpleName()
                        + " does not match dialect " + dialect
                        + "; use YoRealtimeTurnDetection.Qwen for QWEN and "
                        + "YoRealtimeTurnDetection.OpenAi otherwise");
            }
        }

        private static String requireText(@Nullable String value, @NonNull String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value.trim();
        }

        @Nullable
        private static String blankToNull(@Nullable String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
