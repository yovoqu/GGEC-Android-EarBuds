package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次 token 计数请求。两个端点对输入形状要求不同：
 *
 * <ul>
 *   <li>Anthropic {@code /v1/messages/count_tokens}：{@code model + messages (+ system + tools)}；</li>
 *   <li>OpenAI {@code /v1/responses/input_tokens}：{@code model + input}，input 可为字符串或内容项数组。</li>
 * </ul>
 *
 * <p>这里同时容纳两种形状，具体端点只用它需要的字段。</p>
 */
public final class YoTokenCountRequest {

    private final String model;
    private final List<JSONObject> messages;
    @Nullable
    private final String system;
    private final List<JSONObject> tools;
    @Nullable
    private final String inputText;
    private final List<JSONObject> inputItems;

    private YoTokenCountRequest(Builder builder) {
        this.model = builder.model;
        this.messages = Collections.unmodifiableList(new ArrayList<>(builder.messages));
        this.system = builder.system;
        this.tools = Collections.unmodifiableList(new ArrayList<>(builder.tools));
        this.inputText = builder.inputText;
        this.inputItems = Collections.unmodifiableList(new ArrayList<>(builder.inputItems));
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public String model() {
        return model;
    }

    @NonNull
    public List<JSONObject> messages() {
        return messages;
    }

    @Nullable
    public String system() {
        return system;
    }

    @NonNull
    public List<JSONObject> tools() {
        return tools;
    }

    @Nullable
    public String inputText() {
        return inputText;
    }

    @NonNull
    public List<JSONObject> inputItems() {
        return inputItems;
    }

    public static final class Builder {
        private String model;
        private final List<JSONObject> messages = new ArrayList<>();
        @Nullable
        private String system;
        private final List<JSONObject> tools = new ArrayList<>();
        @Nullable
        private String inputText;
        private final List<JSONObject> inputItems = new ArrayList<>();

        private Builder() {
        }

        @NonNull
        public Builder model(@Nullable String model) {
            this.model = model == null ? null : model.trim();
            return this;
        }

        @NonNull
        public Builder addMessage(@Nullable JSONObject message) {
            if (message != null) {
                messages.add(message);
            }
            return this;
        }

        @NonNull
        public Builder system(@Nullable String system) {
            this.system = system == null || system.isBlank() ? null : system;
            return this;
        }

        @NonNull
        public Builder addTool(@Nullable JSONObject tool) {
            if (tool != null) {
                tools.add(tool);
            }
            return this;
        }

        @NonNull
        public Builder inputText(@Nullable String inputText) {
            this.inputText = inputText;
            return this;
        }

        @NonNull
        public Builder addInputItem(@Nullable JSONObject item) {
            if (item != null) {
                inputItems.add(item);
            }
            return this;
        }

        @NonNull
        public YoTokenCountRequest build() {
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            if (messages.isEmpty() && inputText == null && inputItems.isEmpty()) {
                throw new IllegalArgumentException("messages or input are required");
            }
            return new YoTokenCountRequest(this);
        }
    }
}
