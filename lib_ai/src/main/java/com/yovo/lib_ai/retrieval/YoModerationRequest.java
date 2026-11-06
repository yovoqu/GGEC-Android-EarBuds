package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次内容审核请求。输入可以是文本，也可以是 {@code {"type":"image_url", ...}} 一类的内容项，
 * 由审核端点自行支持的模态决定。
 */
public final class YoModerationRequest {

    /** OpenAI 未指定模型时的默认审核模型。 */
    public static final String DEFAULT_MODEL = "omni-moderation-latest";

    @Nullable
    private final String model;
    private final List<Object> input;

    private YoModerationRequest(Builder builder) {
        this.model = builder.model;
        this.input = Collections.unmodifiableList(new ArrayList<>(builder.input));
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public String model() {
        return model;
    }

    /** 输入项，元素为 {@link String} 或 {@link JSONObject}。 */
    @NonNull
    public List<Object> input() {
        return input;
    }

    public static final class Builder {
        @Nullable
        private String model = DEFAULT_MODEL;
        private final List<Object> input = new ArrayList<>();

        private Builder() {
        }

        @NonNull
        public Builder model(@Nullable String model) {
            this.model = model == null || model.isBlank() ? null : model.trim();
            return this;
        }

        @NonNull
        public Builder input(@Nullable String text) {
            if (text != null) {
                input.add(text);
            }
            return this;
        }

        @NonNull
        public Builder addInput(@Nullable JSONObject item) {
            if (item != null) {
                input.add(item);
            }
            return this;
        }

        @NonNull
        public YoModerationRequest build() {
            if (input.isEmpty()) {
                throw new IllegalArgumentException("input is required");
            }
            return new YoModerationRequest(this);
        }
    }
}
