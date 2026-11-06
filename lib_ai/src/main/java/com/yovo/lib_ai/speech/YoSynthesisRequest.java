package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 一次文本合成请求。
 *
 * <p>字段覆盖两种方言（见 {@link Dialect}）：
 * <ul>
 *   <li>OpenAI {@code /v1/audio/speech}：{@code model/input/voice/instructions/response_format/speed/stream_format}；</li>
 *   <li>Qwen {@code /api/v1/services/audio/tts/SpeechSynthesizer}：{@code model} + {@code input.text/voice/format/sample_rate}。</li>
 * </ul>
 *
 * <p>{@code speed} 在 {@link Builder#speed(double)} 就地做范围校验（0.25–4.0），
 * 越界在本地抛 {@link IllegalArgumentException}，不会打到网络上再回一个 400。
 */
public final class YoSynthesisRequest {

    /** 选择线上信封。默认 OpenAI。 */
    public enum Dialect {
        OPENAI,
        QWEN
    }

    /** OpenAI speed 的下界。 */
    public static final double MIN_SPEED = 0.25D;
    /** OpenAI speed 的上界。 */
    public static final double MAX_SPEED = 4.0D;

    private final Dialect dialect;
    private final String input;
    private final String model;
    @Nullable
    private final String voice;
    @Nullable
    private final String instructions;
    @Nullable
    private final String responseFormat;
    @Nullable
    private final Double speed;
    @Nullable
    private final String streamFormat;
    @Nullable
    private final String format;
    @Nullable
    private final Integer sampleRate;

    private YoSynthesisRequest(
            Dialect dialect,
            String input,
            String model,
            @Nullable String voice,
            @Nullable String instructions,
            @Nullable String responseFormat,
            @Nullable Double speed,
            @Nullable String streamFormat,
            @Nullable String format,
            @Nullable Integer sampleRate) {
        this.dialect = dialect;
        this.input = input;
        this.model = model;
        this.voice = voice;
        this.instructions = instructions;
        this.responseFormat = responseFormat;
        this.speed = speed;
        this.streamFormat = streamFormat;
        this.format = format;
        this.sampleRate = sampleRate;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public Dialect dialect() {
        return dialect;
    }

    /** 要合成的文本（OpenAI 的 {@code input}，Qwen 的 {@code input.text}）。 */
    @NonNull
    public String input() {
        return input;
    }

    @NonNull
    public String model() {
        return model;
    }

    @Nullable
    public String voice() {
        return voice;
    }

    @Nullable
    public String instructions() {
        return instructions;
    }

    @Nullable
    public String responseFormat() {
        return responseFormat;
    }

    @Nullable
    public Double speed() {
        return speed;
    }

    @Nullable
    public String streamFormat() {
        return streamFormat;
    }

    @Nullable
    public String format() {
        return format;
    }

    @Nullable
    public Integer sampleRate() {
        return sampleRate;
    }

    public static final class Builder {

        private Dialect dialect = Dialect.OPENAI;
        @Nullable
        private String input;
        @Nullable
        private String model;
        @Nullable
        private String voice;
        @Nullable
        private String instructions;
        @Nullable
        private String responseFormat;
        @Nullable
        private Double speed;
        @Nullable
        private String streamFormat;
        @Nullable
        private String format;
        @Nullable
        private Integer sampleRate;

        private Builder() {
        }

        @NonNull
        public Builder dialect(@NonNull Dialect dialect) {
            this.dialect = Objects.requireNonNull(dialect, "dialect");
            return this;
        }

        /** 要合成的文本。 */
        @NonNull
        public Builder input(@NonNull String input) {
            this.input = trimToNull(input);
            return this;
        }

        @NonNull
        public Builder model(@NonNull String model) {
            this.model = trimToNull(model);
            return this;
        }

        @NonNull
        public Builder voice(@Nullable String voice) {
            this.voice = trimToNull(voice);
            return this;
        }

        @NonNull
        public Builder instructions(@Nullable String instructions) {
            this.instructions = trimToNull(instructions);
            return this;
        }

        /** OpenAI 输出格式，如 {@code mp3/wav/opus/aac/flac/pcm}。 */
        @NonNull
        public Builder responseFormat(@Nullable String responseFormat) {
            this.responseFormat = trimToNull(responseFormat);
            return this;
        }

        /**
         * 语速。必须是 0.25–4.0，越界在本地拒绝。
         */
        @NonNull
        public Builder speed(double speed) {
            if (speed < MIN_SPEED || speed > MAX_SPEED) {
                throw new IllegalArgumentException(
                        "speed must be within " + MIN_SPEED + " and " + MAX_SPEED + ", was " + speed);
            }
            this.speed = speed;
            return this;
        }

        /** OpenAI 流式格式，目前只有 {@code sse}。 */
        @NonNull
        public Builder streamFormat(@Nullable String streamFormat) {
            this.streamFormat = trimToNull(streamFormat);
            return this;
        }

        /** Qwen 音频容器，如 {@code wav}。 */
        @NonNull
        public Builder format(@Nullable String format) {
            this.format = trimToNull(format);
            return this;
        }

        /** Qwen 采样率，如 {@code 24000}。 */
        @NonNull
        public Builder sampleRate(@Nullable Integer sampleRate) {
            if (sampleRate != null && sampleRate <= 0) {
                throw new IllegalArgumentException("sampleRate must be > 0");
            }
            this.sampleRate = sampleRate;
            return this;
        }

        @NonNull
        public YoSynthesisRequest build() {
            if (input == null) {
                throw new IllegalArgumentException("input is required");
            }
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            return new YoSynthesisRequest(
                    dialect,
                    input,
                    model,
                    voice,
                    instructions,
                    responseFormat,
                    speed,
                    streamFormat,
                    format,
                    sampleRate);
        }

        @Nullable
        private static String trimToNull(@Nullable String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
