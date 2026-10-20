package com.yovo.lib_ai.platform.deepseek;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.chat.YoChatPartEncoder;
import com.yovo.lib_ai.chat.YoChatRequestPolicy;
import com.yovo.lib_ai.messages.YoMessagesPolicy;
import com.yovo.lib_ai.models.OpenAiModelListing;
import com.yovo.lib_ai.models.YoModelListing;
import com.yovo.lib_ai.platform.AnthropicHeaders;
import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.responses.YoResponsesPolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek。Chat 文件块使用该主机的扁平形状，Messages 走 Anthropic 兼容路径。
 */
public final class DeepSeekPlatform implements YoPlatform {

    public static final String ID = "deepseek";

    private final String id;
    private final YoServiceTarget openAi;
    private final YoServiceTarget messages;

    private DeepSeekPlatform(String id, YoServiceTarget openAi, YoServiceTarget messages) {
        this.id = id;
        this.openAi = openAi;
        this.messages = messages;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    @Override
    public String id() {
        return id;
    }

    @NonNull
    @Override
    public YoModelListing modelListing() {
        return new OpenAiModelListing(openAi);
    }

    @NonNull
    @Override
    public YoServiceTarget chatTarget() {
        return openAi;
    }

    @NonNull
    @Override
    public YoServiceTarget responsesTarget() {
        return openAi;
    }

    @NonNull
    @Override
    public YoServiceTarget messagesTarget() {
        return messages;
    }

    @NonNull
    @Override
    public YoChatPartEncoder chatPartEncoder() {
        return DeepSeekChatPartEncoder.INSTANCE;
    }

    @NonNull
    @Override
    public YoChatRequestPolicy chatPolicy() {
        return DeepSeekChatPolicy.INSTANCE;
    }

    @NonNull
    @Override
    public YoResponsesPolicy responsesPolicy() {
        return YoResponsesPolicy.none();
    }

    @NonNull
    @Override
    public YoMessagesPolicy messagesPolicy() {
        return YoMessagesPolicy.none();
    }

    @NonNull
    @Override
    public String toString() {
        return "DeepSeekPlatform{baseUrl=" + openAi.baseUrl() + ", apiKey=redacted}";
    }

    public static final class Builder {
        private String id = ID;
        private String baseUrl = "https://api.deepseek.com";
        private String anthropicBaseUrl = "https://api.deepseek.com/anthropic";
        private String apiKey;
        private String version = "2023-06-01";
        private final List<String> betas = new ArrayList<>();

        @NonNull
        public Builder id(@NonNull String id) {
            this.id = YoServiceTarget.requireText(id, "id");
            return this;
        }

        @NonNull
        public Builder baseUrl(@NonNull String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        @NonNull
        public Builder anthropicBaseUrl(@NonNull String anthropicBaseUrl) {
            this.anthropicBaseUrl = anthropicBaseUrl;
            return this;
        }

        @NonNull
        public Builder apiKey(@NonNull String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        @NonNull
        public Builder addBeta(@NonNull String beta) {
            betas.add(YoServiceTarget.requireText(beta, "beta"));
            return this;
        }

        @NonNull
        public DeepSeekPlatform build() {
            String key = YoServiceTarget.requireText(apiKey, "apiKey");
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "Bearer " + key);
            return new DeepSeekPlatform(
                    id,
                    new YoServiceTarget(baseUrl, headers),
                    AnthropicHeaders.target(anthropicBaseUrl, key, version, betas));
        }
    }
}
