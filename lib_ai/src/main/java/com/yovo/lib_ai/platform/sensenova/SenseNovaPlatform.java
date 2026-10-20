package com.yovo.lib_ai.platform.sensenova;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.chat.OpenAiChatPartEncoder;
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
 * SenseNova。Chat 与 Responses 的标准字段走官方编码器，主机差异放在 Responses 策略上。
 */
public final class SenseNovaPlatform implements YoPlatform {

    public static final String ID = "sensenova";

    private final String id;
    private final YoServiceTarget openAi;
    private final YoServiceTarget messages;
    private final YoResponsesPolicy responsesPolicy;

    private SenseNovaPlatform(
            String id,
            YoServiceTarget openAi,
            YoServiceTarget messages,
            YoResponsesPolicy responsesPolicy) {
        this.id = id;
        this.openAi = openAi;
        this.messages = messages;
        this.responsesPolicy = responsesPolicy;
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
        return OpenAiChatPartEncoder.INSTANCE;
    }

    @NonNull
    @Override
    public YoChatRequestPolicy chatPolicy() {
        return YoChatRequestPolicy.none();
    }

    @NonNull
    @Override
    public YoResponsesPolicy responsesPolicy() {
        return responsesPolicy;
    }

    @NonNull
    @Override
    public YoMessagesPolicy messagesPolicy() {
        return YoMessagesPolicy.none();
    }

    @NonNull
    @Override
    public String toString() {
        return "SenseNovaPlatform{baseUrl=" + openAi.baseUrl() + ", apiKey=redacted}";
    }

    public static final class Builder {
        private String id = ID;
        private String baseUrl = "https://token.sensenova.cn";
        private String apiKey;
        private String version = "2023-06-01";
        private final List<String> betas = new ArrayList<>();
        @Nullable
        private Boolean enableThinking;

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
        public Builder enableThinking(boolean enableThinking) {
            this.enableThinking = enableThinking;
            return this;
        }

        @NonNull
        public SenseNovaPlatform build() {
            String key = YoServiceTarget.requireText(apiKey, "apiKey");
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "Bearer " + key);
            return new SenseNovaPlatform(
                    id,
                    new YoServiceTarget(baseUrl, headers),
                    AnthropicHeaders.target(baseUrl, key, version, betas),
                    new SenseNovaResponsesPolicy(enableThinking));
        }
    }
}
