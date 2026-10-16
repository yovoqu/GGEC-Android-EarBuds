package com.yovo.lib_ai.platform.anthropic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.chat.OpenAiChatPartEncoder;
import com.yovo.lib_ai.chat.YoChatPartEncoder;
import com.yovo.lib_ai.chat.YoChatRequestPolicy;
import com.yovo.lib_ai.messages.YoMessagesPolicy;
import com.yovo.lib_ai.platform.AnthropicHeaders;
import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.responses.YoResponsesPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * 官方 Anthropic Messages。
 */
public final class AnthropicPlatform implements YoPlatform {

    public static final String ID = "anthropic";

    private final YoServiceTarget messages;

    private AnthropicPlatform(YoServiceTarget messages) {
        this.messages = messages;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Nullable
    @Override
    public YoServiceTarget chatTarget() {
        return null;
    }

    @Nullable
    @Override
    public YoServiceTarget responsesTarget() {
        return null;
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
        return "AnthropicPlatform{baseUrl=" + messages.baseUrl() + ", apiKey=redacted}";
    }

    public static final class Builder {
        private String baseUrl = "https://api.anthropic.com";
        private String apiKey;
        private String version = "2023-06-01";
        private final List<String> betas = new ArrayList<>();

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
        public Builder version(@NonNull String version) {
            this.version = version;
            return this;
        }

        @NonNull
        public Builder addBeta(@NonNull String beta) {
            betas.add(YoServiceTarget.requireText(beta, "beta"));
            return this;
        }

        @NonNull
        public AnthropicPlatform build() {
            return new AnthropicPlatform(AnthropicHeaders.target(baseUrl, apiKey, version, betas));
        }
    }
}
