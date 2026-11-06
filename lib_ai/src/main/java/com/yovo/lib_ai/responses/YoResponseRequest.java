package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class YoResponseRequest {

    private final String model;
    @Nullable
    private final String inputText;
    private final List<YoResponseInput> inputItems;
    @Nullable
    private final String instructions;
    @Nullable
    private final String previousResponseId;
    private final List<YoResponseTool> tools;
    @Nullable
    private final YoResponseToolChoice toolChoice;
    @Nullable
    private final Boolean parallelToolCalls;
    @Nullable
    private final Integer maxOutputTokens;
    @Nullable
    private final Double temperature;
    @Nullable
    private final Double topP;
    @Nullable
    private final Boolean store;
    @Nullable
    private final YoResponseTextFormat textFormat;
    @Nullable
    private final Boolean generate;
    @Nullable
    private final Boolean background;
    @Nullable
    private final JSONObject contextManagement;
    @Nullable
    private final Object conversation;
    private final List<String> include;
    @Nullable
    private final JSONObject promptCacheOptions;
    @Nullable
    private final JSONObject reasoning;
    @Nullable
    private final String textVerbosity;
    @Nullable
    private final String truncation;
    @Nullable
    private final Integer maxToolCalls;
    @Nullable
    private final String safetyIdentifier;
    @Nullable
    private final String serviceTier;
    @Nullable
    private final JSONObject metadata;

    private YoResponseRequest(
            String model,
            @Nullable String inputText,
            List<YoResponseInput> inputItems,
            @Nullable String instructions,
            @Nullable String previousResponseId,
            List<YoResponseTool> tools,
            @Nullable YoResponseToolChoice toolChoice,
            @Nullable Boolean parallelToolCalls,
            @Nullable Integer maxOutputTokens,
            @Nullable Double temperature,
            @Nullable Double topP,
            @Nullable Boolean store,
            @Nullable YoResponseTextFormat textFormat,
            @Nullable Boolean generate,
            @Nullable Boolean background,
            @Nullable JSONObject contextManagement,
            @Nullable Object conversation,
            List<String> include,
            @Nullable JSONObject promptCacheOptions,
            @Nullable JSONObject reasoning,
            @Nullable String textVerbosity,
            @Nullable String truncation,
            @Nullable Integer maxToolCalls,
            @Nullable String safetyIdentifier,
            @Nullable String serviceTier,
            @Nullable JSONObject metadata) {
        this.model = model;
        this.inputText = inputText;
        this.inputItems = inputItems;
        this.instructions = instructions;
        this.previousResponseId = previousResponseId;
        this.tools = tools;
        this.toolChoice = toolChoice;
        this.parallelToolCalls = parallelToolCalls;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.store = store;
        this.textFormat = textFormat;
        this.generate = generate;
        this.background = background;
        this.contextManagement = contextManagement;
        this.conversation = conversation;
        this.include = include;
        this.promptCacheOptions = promptCacheOptions;
        this.reasoning = reasoning;
        this.textVerbosity = textVerbosity;
        this.truncation = truncation;
        this.maxToolCalls = maxToolCalls;
        this.safetyIdentifier = safetyIdentifier;
        this.serviceTier = serviceTier;
        this.metadata = metadata;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public String model() {
        return model;
    }

    @Nullable
    public String inputText() {
        return inputText;
    }

    @NonNull
    public List<YoResponseInput> inputItems() {
        return inputItems;
    }

    @Nullable
    public String instructions() {
        return instructions;
    }

    @Nullable
    public String previousResponseId() {
        return previousResponseId;
    }

    @NonNull
    public List<YoResponseTool> tools() {
        return tools;
    }

    @Nullable
    public YoResponseToolChoice toolChoice() {
        return toolChoice;
    }

    @Nullable
    public Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    @Nullable
    public Integer maxOutputTokens() {
        return maxOutputTokens;
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
    public Boolean store() {
        return store;
    }

    @Nullable
    public YoResponseTextFormat textFormat() {
        return textFormat;
    }

    /**
     * WebSocket 预热字段。HTTP 调用不会发送它。
     */
    @Nullable
    public Boolean generate() {
        return generate;
    }

    @Nullable
    public Boolean background() {
        return background;
    }

    @Nullable
    public JSONObject contextManagement() {
        return copy(contextManagement);
    }

    /**
     * {@code conversation} 既可以是会话 id 字符串，也可以是对象。
     */
    @Nullable
    public Object conversation() {
        if (conversation instanceof JSONObject json) {
            return copy(json);
        }
        return conversation;
    }

    @NonNull
    public List<String> include() {
        return include;
    }

    @Nullable
    public JSONObject promptCacheOptions() {
        return copy(promptCacheOptions);
    }

    @Nullable
    public JSONObject reasoning() {
        return copy(reasoning);
    }

    @Nullable
    public String textVerbosity() {
        return textVerbosity;
    }

    @Nullable
    public String truncation() {
        return truncation;
    }

    @Nullable
    public Integer maxToolCalls() {
        return maxToolCalls;
    }

    @Nullable
    public String safetyIdentifier() {
        return safetyIdentifier;
    }

    @Nullable
    public String serviceTier() {
        return serviceTier;
    }

    @Nullable
    public JSONObject metadata() {
        return copy(metadata);
    }

    public static final class Builder {
        private String model;
        private String inputText;
        private final List<YoResponseInput> inputItems = new ArrayList<>();
        private boolean itemsSet;
        private String instructions;
        private String previousResponseId;
        private final List<YoResponseTool> tools = new ArrayList<>();
        private YoResponseToolChoice toolChoice;
        private Boolean parallelToolCalls;
        private Integer maxOutputTokens;
        private Double temperature;
        private Double topP;
        private Boolean store;
        private YoResponseTextFormat textFormat;
        private Boolean generate;
        private Boolean background;
        private JSONObject contextManagement;
        private Object conversation;
        private final List<String> include = new ArrayList<>();
        private JSONObject promptCacheOptions;
        private JSONObject reasoning;
        private String textVerbosity;
        private String truncation;
        private Integer maxToolCalls;
        private String safetyIdentifier;
        private String serviceTier;
        private JSONObject metadata;

        @NonNull
        public Builder model(@NonNull String model) {
            if (model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            this.model = model;
            return this;
        }

        @NonNull
        public Builder input(@NonNull String inputText) {
            this.inputText = inputText;
            this.itemsSet = false;
            this.inputItems.clear();
            return this;
        }

        @NonNull
        public Builder inputItems(@NonNull List<YoResponseInput> inputItems) {
            this.inputItems.clear();
            this.inputItems.addAll(inputItems);
            this.itemsSet = true;
            this.inputText = null;
            return this;
        }

        @NonNull
        public Builder addInput(@NonNull YoResponseInput input) {
            if (!itemsSet) {
                inputItems.clear();
                inputText = null;
                itemsSet = true;
            }
            inputItems.add(input);
            return this;
        }

        @NonNull
        public Builder instructions(@Nullable String instructions) {
            this.instructions = instructions;
            return this;
        }

        @NonNull
        public Builder previousResponseId(@Nullable String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        @NonNull
        public Builder addTool(@NonNull YoResponseTool tool) {
            tools.add(tool);
            return this;
        }

        @NonNull
        public Builder toolChoice(@NonNull YoResponseToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        @NonNull
        public Builder parallelToolCalls(boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        @NonNull
        public Builder maxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
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
        public Builder store(boolean store) {
            this.store = store;
            return this;
        }

        @NonNull
        public Builder textFormat(@NonNull YoResponseTextFormat textFormat) {
            this.textFormat = textFormat;
            return this;
        }

        @NonNull
        public Builder generate(boolean generate) {
            this.generate = generate;
            return this;
        }

        @NonNull
        public Builder background(boolean background) {
            this.background = background;
            return this;
        }

        @NonNull
        public Builder contextManagement(@Nullable JSONObject contextManagement) {
            this.contextManagement = copyOrNull(contextManagement);
            return this;
        }

        @NonNull
        public Builder conversation(@Nullable String conversationId) {
            this.conversation = conversationId;
            return this;
        }

        @NonNull
        public Builder conversation(@Nullable JSONObject conversation) {
            this.conversation = copyOrNull(conversation);
            return this;
        }

        @NonNull
        public Builder include(@NonNull List<String> include) {
            this.include.clear();
            this.include.addAll(include);
            return this;
        }

        @NonNull
        public Builder addInclude(@NonNull String include) {
            this.include.add(include);
            return this;
        }

        @NonNull
        public Builder promptCacheOptions(@Nullable JSONObject promptCacheOptions) {
            this.promptCacheOptions = copyOrNull(promptCacheOptions);
            return this;
        }

        @NonNull
        public Builder reasoning(@Nullable JSONObject reasoning) {
            this.reasoning = copyOrNull(reasoning);
            return this;
        }

        @NonNull
        public Builder textVerbosity(@Nullable String textVerbosity) {
            this.textVerbosity = textVerbosity;
            return this;
        }

        @NonNull
        public Builder truncation(@Nullable String truncation) {
            this.truncation = truncation;
            return this;
        }

        @NonNull
        public Builder maxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
            return this;
        }

        @NonNull
        public Builder safetyIdentifier(@Nullable String safetyIdentifier) {
            this.safetyIdentifier = safetyIdentifier;
            return this;
        }

        @NonNull
        public Builder serviceTier(@Nullable String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        @NonNull
        public Builder metadata(@Nullable JSONObject metadata) {
            this.metadata = copyOrNull(metadata);
            return this;
        }

        @NonNull
        public YoResponseRequest build() {
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            if (inputText == null && inputItems.isEmpty()) {
                throw new IllegalArgumentException("input is required");
            }
            return new YoResponseRequest(
                    model,
                    inputText,
                    List.copyOf(inputItems),
                    instructions,
                    previousResponseId,
                    List.copyOf(tools),
                    toolChoice,
                    parallelToolCalls,
                    maxOutputTokens,
                    temperature,
                    topP,
                    store,
                    textFormat,
                    generate,
                    background,
                    copyOrNull(contextManagement),
                    copyConversation(conversation),
                    List.copyOf(include),
                    copyOrNull(promptCacheOptions),
                    copyOrNull(reasoning),
                    textVerbosity,
                    truncation,
                    maxToolCalls,
                    safetyIdentifier,
                    serviceTier,
                    copyOrNull(metadata));
        }

        @Nullable
        private static Object copyConversation(@Nullable Object conversation) {
            if (conversation instanceof JSONObject json) {
                return copyOrNull(json);
            }
            return conversation;
        }
    }

    @Nullable
    private static JSONObject copy(@Nullable JSONObject json) {
        return copyOrNull(json);
    }

    @Nullable
    private static JSONObject copyOrNull(@Nullable JSONObject json) {
        if (json == null) {
            return null;
        }
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }
}
