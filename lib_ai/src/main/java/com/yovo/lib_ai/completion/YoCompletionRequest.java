package com.yovo.lib_ai.completion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次对话补全请求。只放三条协议都能表达的参数；协议专有字段仍走各自的请求类型。
 */
public final class YoCompletionRequest {

    private final String model;
    @Nullable
    private final YoProtocol protocol;
    @Nullable
    private final String system;
    private final List<YoCompletionTurn> turns;
    @Nullable
    private final Double temperature;
    @Nullable
    private final Double topP;
    @Nullable
    private final Integer maxOutputTokens;

    private YoCompletionRequest(Builder builder) {
        this.model = builder.model;
        this.protocol = builder.protocol;
        this.system = builder.system;
        this.turns = List.copyOf(builder.turns);
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.maxOutputTokens = builder.maxOutputTokens;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public String model() {
        return model;
    }

    /**
     * 为空表示使用平台的默认协议。
     */
    @Nullable
    public YoProtocol protocol() {
        return protocol;
    }

    @Nullable
    public String system() {
        return system;
    }

    @NonNull
    public List<YoCompletionTurn> turns() {
        return turns;
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
    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    public static final class Builder {
        private String model;
        private YoProtocol protocol;
        private String system;
        private final List<YoCompletionTurn> turns = new ArrayList<>();
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;

        @NonNull
        public Builder model(@NonNull String model) {
            if (model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            this.model = model.trim();
            return this;
        }

        @NonNull
        public Builder protocol(@Nullable YoProtocol protocol) {
            this.protocol = protocol;
            return this;
        }

        @NonNull
        public Builder system(@Nullable String system) {
            this.system = system == null || system.isBlank() ? null : system;
            return this;
        }

        @NonNull
        public Builder addTurn(@NonNull YoCompletionTurn turn) {
            turns.add(turn);
            return this;
        }

        @NonNull
        public Builder turns(@NonNull List<YoCompletionTurn> turns) {
            this.turns.clear();
            this.turns.addAll(turns);
            return this;
        }

        @NonNull
        public Builder temperature(@Nullable Double temperature) {
            if (temperature != null && (temperature < 0 || temperature > 2)) {
                throw new IllegalArgumentException("temperature must be from 0 to 2");
            }
            this.temperature = temperature;
            return this;
        }

        @NonNull
        public Builder topP(@Nullable Double topP) {
            if (topP != null && (topP <= 0 || topP > 1)) {
                throw new IllegalArgumentException("topP must be in (0, 1]");
            }
            this.topP = topP;
            return this;
        }

        @NonNull
        public Builder maxOutputTokens(@Nullable Integer maxOutputTokens) {
            if (maxOutputTokens != null && maxOutputTokens < 1) {
                throw new IllegalArgumentException("maxOutputTokens must be positive");
            }
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        @NonNull
        public YoCompletionRequest build() {
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            if (turns.isEmpty()) {
                throw new IllegalArgumentException("turns are required");
            }
            if (turns.get(turns.size() - 1).role() != YoCompletionTurn.Role.USER) {
                throw new IllegalArgumentException("the last turn must be a user turn");
            }
            return new YoCompletionRequest(this);
        }
    }
}
