package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 文生图 / 图像编辑请求。
 *
 * <p>两种方言：Qwen 多模态同步（{@code input.messages[].content[].text} + {@code parameters.size/n/...}）
 * 与 OpenAI Images（{@code prompt/model/n/size/response_format}）。字段名差异在实现类里编码。
 */
public final class YoImageRequest {

    /** 线上信封。 */
    public enum Dialect {
        QWEN,
        OPENAI
    }

    private final Dialect dialect;
    private final String prompt;
    private final String model;
    private final int n;
    @Nullable
    private final String size;
    @Nullable
    private final String responseFormat;
    @Nullable
    private final Integer seed;
    @Nullable
    private final String negativePrompt;
    @Nullable
    private final Boolean promptExtend;
    @Nullable
    private final Boolean watermark;
    @Nullable
    private final String referenceImage;

    private YoImageRequest(
            Dialect dialect,
            String prompt,
            String model,
            int n,
            @Nullable String size,
            @Nullable String responseFormat,
            @Nullable Integer seed,
            @Nullable String negativePrompt,
            @Nullable Boolean promptExtend,
            @Nullable Boolean watermark,
            @Nullable String referenceImage) {
        this.dialect = dialect;
        this.prompt = prompt;
        this.model = model;
        this.n = n;
        this.size = size;
        this.responseFormat = responseFormat;
        this.seed = seed;
        this.negativePrompt = negativePrompt;
        this.promptExtend = promptExtend;
        this.watermark = watermark;
        this.referenceImage = referenceImage;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public Dialect dialect() {
        return dialect;
    }

    @NonNull
    public String prompt() {
        return prompt;
    }

    @NonNull
    public String model() {
        return model;
    }

    public int n() {
        return n;
    }

    @Nullable
    public String size() {
        return size;
    }

    @Nullable
    public String responseFormat() {
        return responseFormat;
    }

    @Nullable
    public Integer seed() {
        return seed;
    }

    @Nullable
    public String negativePrompt() {
        return negativePrompt;
    }

    @Nullable
    public Boolean promptExtend() {
        return promptExtend;
    }

    @Nullable
    public Boolean watermark() {
        return watermark;
    }

    /** 编辑时的参考图 URL（Qwen content 里的 {@code image}）。 */
    @Nullable
    public String referenceImage() {
        return referenceImage;
    }

    public static final class Builder {

        private Dialect dialect = Dialect.QWEN;
        @Nullable
        private String prompt;
        @Nullable
        private String model;
        private int n = 1;
        @Nullable
        private String size;
        @Nullable
        private String responseFormat;
        @Nullable
        private Integer seed;
        @Nullable
        private String negativePrompt;
        @Nullable
        private Boolean promptExtend;
        @Nullable
        private Boolean watermark;
        @Nullable
        private String referenceImage;

        private Builder() {
        }

        @NonNull
        public Builder dialect(@NonNull Dialect dialect) {
            this.dialect = Objects.requireNonNull(dialect, "dialect");
            return this;
        }

        @NonNull
        public Builder prompt(@NonNull String prompt) {
            this.prompt = trimToNull(prompt);
            return this;
        }

        @NonNull
        public Builder model(@NonNull String model) {
            this.model = trimToNull(model);
            return this;
        }

        /** 生成张数，必须 ≥ 1。 */
        @NonNull
        public Builder n(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("n must be >= 1");
            }
            this.n = n;
            return this;
        }

        @NonNull
        public Builder size(@Nullable String size) {
            this.size = trimToNull(size);
            return this;
        }

        /** OpenAI 输出格式：{@code url} 或 {@code b64_json}。 */
        @NonNull
        public Builder responseFormat(@Nullable String responseFormat) {
            this.responseFormat = trimToNull(responseFormat);
            return this;
        }

        @NonNull
        public Builder seed(@Nullable Integer seed) {
            this.seed = seed;
            return this;
        }

        @NonNull
        public Builder negativePrompt(@Nullable String negativePrompt) {
            this.negativePrompt = trimToNull(negativePrompt);
            return this;
        }

        @NonNull
        public Builder promptExtend(@Nullable Boolean promptExtend) {
            this.promptExtend = promptExtend;
            return this;
        }

        @NonNull
        public Builder watermark(@Nullable Boolean watermark) {
            this.watermark = watermark;
            return this;
        }

        @NonNull
        public Builder referenceImage(@Nullable String referenceImage) {
            this.referenceImage = trimToNull(referenceImage);
            return this;
        }

        @NonNull
        public YoImageRequest build() {
            if (prompt == null) {
                throw new IllegalArgumentException("prompt is required");
            }
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            return new YoImageRequest(
                    dialect, prompt, model, n, size, responseFormat, seed,
                    negativePrompt, promptExtend, watermark, referenceImage);
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
