package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * 一次转写请求：音频字节 + 它想被当成什么 + 少量识别提示。
 *
 * <p>为什么 {@code mimeType} 是字段而不是常量：采集侧交出的是裸 PCM16，
 * 而"先落盘成 wav 再读回来"的路径交出的是已经带头的 wav。两种字节都要能进来，
 * 于是用 {@code mimeType} 声明输入是什么，{@link YoWhisperSpeech} 据此决定要不要包
 * {@link WavEncoder} 的头——判断集中在发送前一处，不在调用方各写一遍。
 *
 * <p>为什么 {@code language} 和 {@code prompt} 可空：它们是识别提示，不是必填协议字段。
 * {@code language} 知道就传（能减少识别完再翻译的错误），不知道就让服务端自动判定；
 * {@code prompt} 用来给生僻词、专有名词一点上下文，没有就不发这个 part，
 * 发空串在某些网关上反而会被当成"提示为空"的无效参数。
 */
public final class YoTranscriptionRequest {

    /** 已封装好的 wav 字节。 */
    public static final String MIME_WAV = "audio/wav";

    /** 裸 PCM16 小端字节（16kHz / 单声道），发送前由本库包成 wav。 */
    public static final String MIME_PCM16 = "audio/pcm";

    /** 上传文件名。固定不变，服务端按扩展名或魔数识别，不需要调用方操心。 */
    public static final String FILENAME = "audio.wav";

    private final byte[] audio;
    private final String mimeType;
    @Nullable
    private final String language;
    private final String model;
    @Nullable
    private final String prompt;

    private YoTranscriptionRequest(
            byte[] audio,
            String mimeType,
            @Nullable String language,
            String model,
            @Nullable String prompt) {
        this.audio = audio;
        this.mimeType = mimeType;
        this.language = language;
        this.model = model;
        this.prompt = prompt;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /** 便捷入口：已有 wav 字节。 */
    @NonNull
    public static YoTranscriptionRequest wav(@NonNull byte[] wavBytes, @NonNull String model) {
        return builder().wav(wavBytes).model(model).build();
    }

    /** 便捷入口：采集到的裸 PCM16 字节，发送时自动包 44 字节头。 */
    @NonNull
    public static YoTranscriptionRequest pcm16(@NonNull byte[] pcm16, @NonNull String model) {
        return builder().pcm16(pcm16).model(model).build();
    }

    /** 传入字节的编码声明（已被规范化的小写形式）。 */
    @NonNull
    public String mimeType() {
        return mimeType;
    }

    /** 语种提示，如 {@code zh}、{@code en}；未知时为 null。 */
    @Nullable
    public String language() {
        return language;
    }

    /** 转写模型，如 {@code whisper-1}；由调用方按平台配置传入，本库不写死默认值。 */
    @NonNull
    public String model() {
        return model;
    }

    /** 上下文提示，用于纠正专有名词；未设置时为 null。 */
    @Nullable
    public String prompt() {
        return prompt;
    }

    /** 防御性拷贝：请求对象可能在另一条线程上被读取，不能让调用方后续改动影响上传内容。 */
    @NonNull
    public byte[] audioBytes() {
        return audio.clone();
    }

    /**
     * 实际送出的字节：输入是裸 PCM 时在这里包 wav，已经是 wav 时原样返回。
     *
     * <p>包在发送前而不是构造时：{@link #audioBytes()} 保持调用方交进来的原貌，
     * 排查"我传的对不对"时不会看到多出来的 44 字节。
     */
    @NonNull
    byte[] payload() {
        return isRawPcm(mimeType) ? WavEncoder.encode(audio) : audio;
    }

    /** 文件 part 的媒体类型。不管进来的是 PCM 还是 wav，送出的都已经是 wav。 */
    @NonNull
    String fileContentType() {
        return MIME_WAV;
    }

    static boolean isRawPcm(String mimeType) {
        String normalized = baseMimeType(mimeType);
        return "audio/pcm".equals(normalized)
                || "audio/pcm16".equals(normalized)
                || "audio/l16".equals(normalized)
                || "audio/raw".equals(normalized);
    }

    private static String baseMimeType(String mimeType) {
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        return parameters < 0 ? normalized : normalized.substring(0, parameters).trim();
    }

    public static final class Builder {

        @Nullable
        private byte[] audio;
        private String mimeType = MIME_WAV;
        @Nullable
        private String language;
        @Nullable
        private String model;
        @Nullable
        private String prompt;

        private Builder() {
        }

        /** 声明字节是已经封装的 wav。 */
        @NonNull
        public Builder wav(@NonNull byte[] wavBytes) {
            return audio(wavBytes, MIME_WAV);
        }

        /** 声明字节是裸 PCM16（16kHz / 单声道 / 小端）。 */
        @NonNull
        public Builder pcm16(@NonNull byte[] pcm16) {
            return audio(pcm16, MIME_PCM16);
        }

        @NonNull
        public Builder audio(@NonNull byte[] bytes, @NonNull String mimeType) {
            this.audio = Objects.requireNonNull(bytes, "audioBytes").clone();
            String normalized = baseMimeType(Objects.requireNonNull(mimeType, "mimeType"));
            this.mimeType = normalized.isEmpty() ? MIME_WAV : normalized;
            return this;
        }

        /** 语种提示；传 null 表示交给服务端自动判定。 */
        @NonNull
        public Builder language(@Nullable String language) {
            this.language = trimToNull(language);
            return this;
        }

        @NonNull
        public Builder model(@NonNull String model) {
            this.model = trimToNull(Objects.requireNonNull(model, "model"));
            return this;
        }

        /** 上下文提示；传 null 表示不发这个 part。 */
        @NonNull
        public Builder prompt(@Nullable String prompt) {
            this.prompt = trimToNull(prompt);
            return this;
        }

        /**
         * @throws IllegalArgumentException 没给音频、给了空音频、没给模型，
         *                                  或语种里出现换行等控制字符
         */
        @NonNull
        public YoTranscriptionRequest build() {
            if (audio == null) {
                throw new IllegalArgumentException("audioBytes is required");
            }
            if (audio.length == 0) {
                throw new IllegalArgumentException("audioBytes is empty; skip the request instead");
            }
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            if (language != null && containsControlCharacter(language)) {
                throw new IllegalArgumentException("language must be a single token without control characters");
            }
            return new YoTranscriptionRequest(
                    audio.clone(),
                    mimeType,
                    language,
                    model,
                    prompt);
        }

        @Nullable
        private static String trimToNull(@Nullable String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private static boolean containsControlCharacter(String value) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 0x20 || c == 0x7f) {
                    return true;
                }
            }
            return false;
        }
    }
}
