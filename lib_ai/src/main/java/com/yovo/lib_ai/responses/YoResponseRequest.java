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
    private final JSONObject extraFields;

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
            @Nullable JSONObject extraFields) {
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
        this.extraFields = extraFields;
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
    public JSONObject extraFields() {
        if (extraFields == null) {
            return null;
        }
        try {
            return new JSONObject(extraFields.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException(exception);
        }
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
        private JSONObject extraFields;

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
        public Builder extraFields(@NonNull JSONObject extraFields) {
            try {
                this.extraFields = new JSONObject(extraFields.toString());
            } catch (JSONException exception) {
                throw new IllegalArgumentException(exception);
            }
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
                    extraFields);
        }
    }
}
