package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class YoMessageRequest {

    private final String model;
    private final int maxTokens;
    private final List<YoMessageTurn> messages;
    @Nullable
    private final String system;
    private final List<YoMessageTool> tools;
    private final List<YoMessageServerTool> serverTools;
    @Nullable
    private final YoMessageToolChoice toolChoice;
    @Nullable
    private final Double temperature;
    @Nullable
    private final Double topP;
    private final List<String> stopSequences;
    @Nullable
    private final String userId;
    @Nullable
    private final Integer topK;
    @Nullable
    private final YoThinkingConfig thinking;
    @Nullable
    private final JSONObject cacheControl;
    @Nullable
    private final JSONObject outputConfig;
    @Nullable
    private final JSONObject metadata;
    @Nullable
    private final String serviceTier;

    private YoMessageRequest(
            String model,
            int maxTokens,
            List<YoMessageTurn> messages,
            @Nullable String system,
            List<YoMessageTool> tools,
            List<YoMessageServerTool> serverTools,
            @Nullable YoMessageToolChoice toolChoice,
            @Nullable Double temperature,
            @Nullable Double topP,
            List<String> stopSequences,
            @Nullable String userId,
            @Nullable Integer topK,
            @Nullable YoThinkingConfig thinking,
            @Nullable JSONObject cacheControl,
            @Nullable JSONObject outputConfig,
            @Nullable JSONObject metadata,
            @Nullable String serviceTier) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.messages = messages;
        this.system = system;
        this.tools = tools;
        this.serverTools = serverTools;
        this.toolChoice = toolChoice;
        this.temperature = temperature;
        this.topP = topP;
        this.stopSequences = stopSequences;
        this.userId = userId;
        this.topK = topK;
        this.thinking = thinking;
        this.cacheControl = cacheControl;
        this.outputConfig = outputConfig;
        this.metadata = metadata;
        this.serviceTier = serviceTier;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public String model() {
        return model;
    }

    public int maxTokens() {
        return maxTokens;
    }

    @NonNull
    public List<YoMessageTurn> messages() {
        return messages;
    }

    @Nullable
    public String system() {
        return system;
    }

    @NonNull
    public List<YoMessageTool> tools() {
        return tools;
    }

    @NonNull
    public List<YoMessageServerTool> serverTools() {
        return serverTools;
    }

    @Nullable
    public YoMessageToolChoice toolChoice() {
        return toolChoice;
    }

    @Nullable
    public Double temperature() {
        return temperature;
    }

    @Nullable
    public Double topP() {
        return topP;
    }

    @NonNull
    public List<String> stopSequences() {
        return stopSequences;
    }

    @Nullable
    public String userId() {
        return userId;
    }

    @Nullable
    public Integer topK() {
        return topK;
    }

    @Nullable
    public YoThinkingConfig thinking() {
        return thinking;
    }

    /**
     * 请求级 cache_control；编码时挂到 system 文本块上。
     */
    @Nullable
    public JSONObject cacheControl() {
        return cacheControl == null ? null : copy(cacheControl);
    }

    @Nullable
    public JSONObject outputConfig() {
        return outputConfig == null ? null : copy(outputConfig);
    }

    @Nullable
    public JSONObject metadata() {
        return metadata == null ? null : copy(metadata);
    }

    @Nullable
    public String serviceTier() {
        return serviceTier;
    }

    public static final class Builder {
        private String model;
        private Integer maxTokens;
        private final List<YoMessageTurn> messages = new ArrayList<>();
        private String system;
        private final List<YoMessageTool> tools = new ArrayList<>();
        private final List<YoMessageServerTool> serverTools = new ArrayList<>();
        private YoMessageToolChoice toolChoice;
        private Double temperature;
        private Double topP;
        private final List<String> stopSequences = new ArrayList<>();
        private String userId;
        private Integer topK;
        private YoThinkingConfig thinking;
        private JSONObject cacheControl;
        private JSONObject outputConfig;
        private JSONObject metadata;
        private String serviceTier;

        @NonNull
        public Builder model(@NonNull String model) {
            if (model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            this.model = model;
            return this;
        }

        @NonNull
        public Builder maxTokens(int maxTokens) {
            if (maxTokens < 1) {
                throw new IllegalArgumentException("maxTokens must be positive");
            }
            this.maxTokens = maxTokens;
            return this;
        }

        @NonNull
        public Builder addMessage(@NonNull YoMessageTurn message) {
            messages.add(message);
            return this;
        }

        @NonNull
        public Builder addUserMessage(@NonNull String text) {
            return addMessage(YoMessageTurn.user(text));
        }

        @NonNull
        public Builder system(@Nullable String system) {
            this.system = system;
            return this;
        }

        @NonNull
        public Builder addTool(@NonNull YoMessageTool tool) {
            tools.add(tool);
            return this;
        }

        @NonNull
        public Builder addServerTool(@NonNull YoMessageServerTool tool) {
            serverTools.add(tool);
            return this;
        }

        @NonNull
        public Builder toolChoice(@NonNull YoMessageToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        @NonNull
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        @NonNull
        public Builder topP(double topP) {
            this.topP = topP;
            return this;
        }

        @NonNull
        public Builder addStopSequence(@NonNull String stopSequence) {
            stopSequences.add(stopSequence);
            return this;
        }

        @NonNull
        public Builder userId(@Nullable String userId) {
            this.userId = userId;
            return this;
        }

        @NonNull
        public Builder topK(int topK) {
            if (topK < 0) {
                throw new IllegalArgumentException("topK must be >= 0");
            }
            this.topK = topK;
            return this;
        }

        @NonNull
        public Builder thinking(@Nullable YoThinkingConfig thinking) {
            this.thinking = thinking;
            return this;
        }

        @NonNull
        public Builder cacheControl(@Nullable JSONObject cacheControl) {
            this.cacheControl = cacheControl == null ? null : copy(cacheControl);
            return this;
        }

        @NonNull
        public Builder outputConfig(@Nullable JSONObject outputConfig) {
            this.outputConfig = outputConfig == null ? null : copy(outputConfig);
            return this;
        }

        @NonNull
        public Builder metadata(@Nullable JSONObject metadata) {
            this.metadata = metadata == null ? null : copy(metadata);
            return this;
        }

        @NonNull
        public Builder serviceTier(@Nullable String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        @NonNull
        public YoMessageRequest build() {
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            if (maxTokens == null) {
                throw new IllegalArgumentException("maxTokens is required");
            }
            if (messages.isEmpty()) {
                throw new IllegalArgumentException("messages are required");
            }
            return new YoMessageRequest(
                    model,
                    maxTokens,
                    List.copyOf(messages),
                    system,
                    List.copyOf(tools),
                    List.copyOf(serverTools),
                    toolChoice,
                    temperature,
                    topP,
                    List.copyOf(stopSequences),
                    userId,
                    topK,
                    thinking,
                    cacheControl == null ? null : copy(cacheControl),
                    outputConfig == null ? null : copy(outputConfig),
                    metadata == null ? null : copy(metadata),
                    serviceTier);
        }
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }
}
