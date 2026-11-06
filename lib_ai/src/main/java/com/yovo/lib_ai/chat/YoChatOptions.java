package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Chat Completions 的低频/厂商特有参数。
 *
 * <p>为什么不直接塞进 {@link YoChatCompletionRequest}：这些字段大多只对部分模型或
 * 部分厂商生效，放主类会让构造器和序列化日志都变得难以阅读；集中在一个可选对象里，
 * 由方言做支持性校验，主请求保持稳定。</p>
 *
 * <p>{@code extraBody} 是逃生舱：原样合并到请求体，但**不覆盖**任何已显式设置的字段。</p>
 */
public final class YoChatOptions {

    @Nullable
    private final Integer topK;
    @Nullable
    private final Boolean logprobs;
    @Nullable
    private final Integer topLogprobs;
    @Nullable
    private final JSONObject logitBias;
    @Nullable
    private final String user;
    @Nullable
    private final String safetyIdentifier;
    @Nullable
    private final String promptCacheKey;
    @Nullable
    private final JSONObject promptCacheOptions;
    @Nullable
    private final JSONObject audio;
    @Nullable
    private final JSONObject webSearchOptions;
    @Nullable
    private final JSONObject thinking;
    @Nullable
    private final JSONObject extraBody;

    private YoChatOptions(Builder builder) {
        this.topK = builder.topK;
        this.logprobs = builder.logprobs;
        this.topLogprobs = builder.topLogprobs;
        this.logitBias = copy(builder.logitBias);
        this.user = blankToNull(builder.user);
        this.safetyIdentifier = blankToNull(builder.safetyIdentifier);
        this.promptCacheKey = blankToNull(builder.promptCacheKey);
        this.promptCacheOptions = copy(builder.promptCacheOptions);
        this.audio = copy(builder.audio);
        this.webSearchOptions = copy(builder.webSearchOptions);
        this.thinking = copy(builder.thinking);
        this.extraBody = copy(builder.extraBody);
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @Nullable public Integer topK() { return topK; }
    @Nullable public Boolean logprobs() { return logprobs; }
    @Nullable public Integer topLogprobs() { return topLogprobs; }
    @Nullable public JSONObject logitBias() { return copy(logitBias); }
    @Nullable public String user() { return user; }
    @Nullable public String safetyIdentifier() { return safetyIdentifier; }
    @Nullable public String promptCacheKey() { return promptCacheKey; }
    @Nullable public JSONObject promptCacheOptions() { return copy(promptCacheOptions); }
    @Nullable public JSONObject audio() { return copy(audio); }
    @Nullable public JSONObject webSearchOptions() { return copy(webSearchOptions); }
    @Nullable public JSONObject thinking() { return copy(thinking); }
    @Nullable public JSONObject extraBody() { return copy(extraBody); }

    public boolean isEmpty() {
        return topK == null && logprobs == null && topLogprobs == null && logitBias == null
                && user == null && safetyIdentifier == null && promptCacheKey == null
                && promptCacheOptions == null && audio == null && webSearchOptions == null
                && thinking == null && extraBody == null;
    }

    /** 把本对象写入已构造好的请求体；同名字段不覆盖显式值。 */
    public void applyTo(@NonNull JSONObject body) throws JSONException {
        if (topK != null) body.put("top_k", topK);
        if (logprobs != null) body.put("logprobs", logprobs);
        if (topLogprobs != null) body.put("top_logprobs", topLogprobs);
        if (logitBias != null) body.put("logit_bias", logitBias);
        if (user != null) body.put("user", user);
        if (safetyIdentifier != null) body.put("safety_identifier", safetyIdentifier);
        if (promptCacheKey != null) body.put("prompt_cache_key", promptCacheKey);
        if (promptCacheOptions != null) body.put("prompt_cache_options", promptCacheOptions);
        if (audio != null) body.put("audio", audio);
        if (webSearchOptions != null) body.put("web_search_options", webSearchOptions);
        if (thinking != null) body.put("thinking", thinking);
        if (extraBody != null) {
            java.util.Iterator<String> keys = extraBody.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!body.has(key)) {
                    body.put(key, extraBody.get(key));
                }
            }
        }
    }

    @Nullable
    private static JSONObject copy(@Nullable JSONObject json) {
        if (json == null) {
            return null;
        }
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static final class Builder {
        private Integer topK;
        private Boolean logprobs;
        private Integer topLogprobs;
        private JSONObject logitBias;
        private String user;
        private String safetyIdentifier;
        private String promptCacheKey;
        private JSONObject promptCacheOptions;
        private JSONObject audio;
        private JSONObject webSearchOptions;
        private JSONObject thinking;
        private JSONObject extraBody;

        private Builder() {
        }

        @NonNull
        public Builder topK(@Nullable Integer topK) {
            if (topK != null && topK < 0) {
                throw new IllegalArgumentException("topK must be >= 0");
            }
            this.topK = topK;
            return this;
        }

        @NonNull
        public Builder logprobs(@Nullable Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        @NonNull
        public Builder topLogprobs(@Nullable Integer topLogprobs) {
            if (topLogprobs != null && (topLogprobs < 0 || topLogprobs > 20)) {
                throw new IllegalArgumentException("topLogprobs must be from 0 to 20");
            }
            this.topLogprobs = topLogprobs;
            return this;
        }

        @NonNull
        public Builder logitBias(@Nullable JSONObject logitBias) {
            this.logitBias = copy(logitBias);
            return this;
        }

        @NonNull
        public Builder user(@Nullable String user) {
            this.user = user;
            return this;
        }

        @NonNull
        public Builder safetyIdentifier(@Nullable String safetyIdentifier) {
            this.safetyIdentifier = safetyIdentifier;
            return this;
        }

        @NonNull
        public Builder promptCacheKey(@Nullable String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return this;
        }

        @NonNull
        public Builder promptCacheOptions(@Nullable JSONObject promptCacheOptions) {
            this.promptCacheOptions = copy(promptCacheOptions);
            return this;
        }

        @NonNull
        public Builder audio(@Nullable JSONObject audio) {
            this.audio = copy(audio);
            return this;
        }

        @NonNull
        public Builder webSearchOptions(@Nullable JSONObject webSearchOptions) {
            this.webSearchOptions = copy(webSearchOptions);
            return this;
        }

        @NonNull
        public Builder thinking(@Nullable JSONObject thinking) {
            this.thinking = copy(thinking);
            return this;
        }

        @NonNull
        public Builder extraBody(@Nullable JSONObject extraBody) {
            this.extraBody = copy(extraBody);
            return this;
        }

        @NonNull
        public YoChatOptions build() {
            return new YoChatOptions(this);
        }
    }
}
