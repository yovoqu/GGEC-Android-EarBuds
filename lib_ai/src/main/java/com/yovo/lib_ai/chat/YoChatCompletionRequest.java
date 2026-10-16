package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class YoChatCompletionRequest {

    private final String model;
    private final List<YoChatMessage> messages;
    @Nullable
    private final Double temperature;
    @Nullable
    private final Double topP;
    @Nullable
    private final Integer maxCompletionTokens;
    private final List<YoChatTool> tools;
    @Nullable
    private final YoChatToolChoice toolChoice;
    @Nullable
    private final YoChatResponseFormat responseFormat;
    private final List<String> stop;
    @Nullable
    private final Boolean parallelToolCalls;
    @Nullable
    private final String reasoningEffort;
    @Nullable
    private final Double frequencyPenalty;
    @Nullable
    private final Double presencePenalty;
    @Nullable
    private final Integer n;
    @Nullable
    private final Integer seed;
    @Nullable
    private final Boolean store;
    @Nullable
    private final String serviceTier;
    @Nullable
    private final JSONObject metadata;
    private final List<String> modalities;

    private YoChatCompletionRequest(
            String model,
            List<YoChatMessage> messages,
            @Nullable Double temperature,
            @Nullable Double topP,
            @Nullable Integer maxCompletionTokens,
            List<YoChatTool> tools,
            @Nullable YoChatToolChoice toolChoice,
            @Nullable YoChatResponseFormat responseFormat,
            List<String> stop,
            @Nullable Boolean parallelToolCalls,
            @Nullable String reasoningEffort,
            @Nullable Double frequencyPenalty,
            @Nullable Double presencePenalty,
            @Nullable Integer n,
            @Nullable Integer seed,
            @Nullable Boolean store,
            @Nullable String serviceTier,
            @Nullable JSONObject metadata,
            List<String> modalities) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.topP = topP;
        this.maxCompletionTokens = maxCompletionTokens;
        this.tools = tools;
        this.toolChoice = toolChoice;
        this.responseFormat = responseFormat;
        this.stop = stop;
        this.parallelToolCalls = parallelToolCalls;
        this.reasoningEffort = reasoningEffort;
        this.frequencyPenalty = frequencyPenalty;
        this.presencePenalty = presencePenalty;
        this.n = n;
        this.seed = seed;
        this.store = store;
        this.serviceTier = serviceTier;
        this.metadata = metadata;
        this.modalities = modalities;
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
    public List<YoChatMessage> messages() {
        return messages;
    }

    @Nullable
    public Double temperature() {
        return temperature;
    }

    @Nullable
    public Double topP() {
        return topP;
    }

    @Nullable
    public Integer maxCompletionTokens() {
        return maxCompletionTokens;
    }

    @NonNull
    public List<YoChatTool> tools() {
        return tools;
    }

    @Nullable
    public YoChatToolChoice toolChoice() {
        return toolChoice;
    }

    @Nullable
    public YoChatResponseFormat responseFormat() {
        return responseFormat;
    }

    @NonNull
    public List<String> stop() {
        return stop;
    }

    @Nullable
    public Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    @Nullable
    public String reasoningEffort() {
        return reasoningEffort;
    }

    @Nullable
    public Double frequencyPenalty() {
        return frequencyPenalty;
    }

    @Nullable
    public Double presencePenalty() {
        return presencePenalty;
    }

    @Nullable
    public Integer n() {
        return n;
    }

    @Nullable
    public Integer seed() {
        return seed;
    }

    @Nullable
    public Boolean store() {
        return store;
    }

    @Nullable
    public String serviceTier() {
        return serviceTier;
    }

    @Nullable
    public JSONObject metadata() {
        return metadata == null ? null : copy(metadata);
    }

    @NonNull
    public List<String> modalities() {
        return modalities;
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }

    public static final class Builder {
        private String model;
        private final List<YoChatMessage> messages = new ArrayList<>();
        private Double temperature;
        private Double topP;
        private Integer maxCompletionTokens;
        private final List<YoChatTool> tools = new ArrayList<>();
        private YoChatToolChoice toolChoice;
        private YoChatResponseFormat responseFormat;
        private final List<String> stop = new ArrayList<>();
        private Boolean parallelToolCalls;
        private String reasoningEffort;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer n;
        private Integer seed;
        private Boolean store;
        private String serviceTier;
        private JSONObject metadata;
        private final List<String> modalities = new ArrayList<>();

        @NonNull
        public Builder model(@NonNull String model) {
            if (model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            this.model = model;
            return this;
        }

        @NonNull
        public Builder addMessage(@NonNull YoChatMessage message) {
            messages.add(message);
            return this;
        }

        @NonNull
        public Builder addSystemMessage(@NonNull String content) {
            return addMessage(YoChatMessage.system(content));
        }

        @NonNull
        public Builder addUserMessage(@NonNull String content) {
            return addMessage(YoChatMessage.user(content));
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
        public Builder maxCompletionTokens(int maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        @NonNull
        public Builder addTool(@NonNull YoChatTool tool) {
            tools.add(tool);
            return this;
        }

        @NonNull
        public Builder toolChoice(@NonNull YoChatToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        @NonNull
        public Builder responseFormat(@NonNull YoChatResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        @NonNull
        public Builder addStop(@NonNull String stop) {
            this.stop.add(stop);
            return this;
        }

        @NonNull
        public Builder parallelToolCalls(boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        @NonNull
        public Builder reasoningEffort(@NonNull String reasoningEffort) {
            this.reasoningEffort = switch (reasoningEffort) {
                case "none", "minimal", "low", "medium", "high", "xhigh", "max" -> reasoningEffort;
                default -> throw new IllegalArgumentException(
                        "reasoningEffort must be none, minimal, low, medium, high, xhigh, or max");
            };
            return this;
        }

        @NonNull
        public Builder frequencyPenalty(double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        @NonNull
        public Builder presencePenalty(double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        @NonNull
        public Builder n(int n) {
            if (n < 1 || n > 128) {
                throw new IllegalArgumentException("n must be from 1 to 128");
            }
            this.n = n;
            return this;
        }

        @NonNull
        public Builder seed(int seed) {
            this.seed = seed;
            return this;
        }

        @NonNull
        public Builder store(boolean store) {
            this.store = store;
            return this;
        }

        @NonNull
        public Builder serviceTier(@NonNull String serviceTier) {
            this.serviceTier = switch (serviceTier) {
                case "auto", "default", "flex", "scale", "priority", "fast" -> serviceTier;
                default -> throw new IllegalArgumentException(
                        "serviceTier must be auto, default, flex, scale, priority, or fast");
            };
            return this;
        }

        @NonNull
        public Builder metadata(@NonNull JSONObject metadata) {
            this.metadata = copy(metadata);
            return this;
        }

        @NonNull
        public Builder addModality(@NonNull String modality) {
            if (!modality.equals("text") && !modality.equals("audio")) {
                throw new IllegalArgumentException("modality must be text or audio");
            }
            modalities.add(modality);
            return this;
        }

        @NonNull
        public YoChatCompletionRequest build() {
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            if (messages.isEmpty()) {
                throw new IllegalArgumentException("messages are required");
            }
            return new YoChatCompletionRequest(
                    model,
                    List.copyOf(messages),
                    temperature,
                    topP,
                    maxCompletionTokens,
                    List.copyOf(tools),
                    toolChoice,
                    responseFormat,
                    List.copyOf(stop),
                    parallelToolCalls,
                    reasoningEffort,
                    frequencyPenalty,
                    presencePenalty,
                    n,
                    seed,
                    store,
                    serviceTier,
                    metadata,
                    List.copyOf(modalities));
        }
    }
}
