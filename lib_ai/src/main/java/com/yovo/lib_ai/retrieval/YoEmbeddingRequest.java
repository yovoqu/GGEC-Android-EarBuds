package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次向量化请求。语义只表达"把哪些文本映射成向量"，不含任何 wire 字段名。
 *
 * <p>同一条请求可被 OpenAI 兼容端点与 DashScope 原生端点编码：两家都收模型、一批文本、
 * 可选维度；OpenAI 另有 {@code encoding_format}，DashScope 用 {@code parameters.output_type} 表达。</p>
 *
 * <p>批大小上限由模型决定（qwen3.7 ≤20、v3/v4 ≤10、v1/v2 ≤25），这里不做平台相关校验，
 * 越界由服务端报错；本地只挡明显无意义的空模型与空输入。</p>
 */
public final class YoEmbeddingRequest {

    private final String model;
    private final List<String> inputs;
    @Nullable
    private final Integer dimensions;
    @Nullable
    private final String encodingFormat;

    private YoEmbeddingRequest(Builder builder) {
        this.model = builder.model;
        this.inputs = Collections.unmodifiableList(new ArrayList<>(builder.inputs));
        this.dimensions = builder.dimensions;
        this.encodingFormat = builder.encodingFormat;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /** 向量模型名，例如 {@code text-embedding-3-small}、{@code qwen3.7-text-embedding}。 */
    @NonNull
    public String model() {
        return model;
    }

    /** 待向量化的文本，按输入顺序。 */
    @NonNull
    public List<String> inputs() {
        return inputs;
    }

    /** 目标维度；为空时由模型默认值决定。 */
    @Nullable
    public Integer dimensions() {
        return dimensions;
    }

    /** OpenAI 的 {@code encoding_format}（{@code float} / {@code base64}）；DashScope 忽略。 */
    @Nullable
    public String encodingFormat() {
        return encodingFormat;
    }

    public static final class Builder {

        private String model;
        private final List<String> inputs = new ArrayList<>();
        @Nullable
        private Integer dimensions;
        @Nullable
        private String encodingFormat;

        private Builder() {
        }

        @NonNull
        public Builder model(@Nullable String model) {
            this.model = model == null ? null : model.trim();
            return this;
        }

        /** 追加一条待向量化文本。 */
        @NonNull
        public Builder input(@Nullable String text) {
            if (text != null) {
                inputs.add(text);
            }
            return this;
        }

        @NonNull
        public Builder inputs(@Nullable List<String> texts) {
            if (texts != null) {
                for (String text : texts) {
                    if (text != null) {
                        inputs.add(text);
                    }
                }
            }
            return this;
        }

        @NonNull
        public Builder dimensions(@Nullable Integer dimensions) {
            if (dimensions != null && dimensions < 1) {
                throw new IllegalArgumentException("dimensions must be positive");
            }
            this.dimensions = dimensions;
            return this;
        }

        @NonNull
        public Builder encodingFormat(@Nullable String encodingFormat) {
            this.encodingFormat = encodingFormat == null || encodingFormat.isBlank()
                    ? null : encodingFormat.trim();
            return this;
        }

        @NonNull
        public YoEmbeddingRequest build() {
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("at least one input is required");
            }
            return new YoEmbeddingRequest(this);
        }
    }
}
