package com.yovo.lib_ai.platform.openai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.chat.OpenAiChatPartEncoder;
import com.yovo.lib_ai.chat.YoChatPartEncoder;
import com.yovo.lib_ai.chat.YoChatRequestPolicy;
import com.yovo.lib_ai.messages.YoMessagesPolicy;
import com.yovo.lib_ai.models.OpenAiModelListing;
import com.yovo.lib_ai.models.YoModelListing;
import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.responses.YoResponsesPolicy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 官方 OpenAI。Chat 与 Responses 走标准编码器。
 */
public final class OpenAiPlatform implements YoPlatform {

    public static final String ID = "openai";

    private final String id;
    private final YoServiceTarget target;

    private OpenAiPlatform(String id, YoServiceTarget target) {
        this.id = id;
        this.target = target;
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
        return new OpenAiModelListing(target);
    }

    @NonNull
    @Override
    public YoServiceTarget chatTarget() {
        return target;
    }

    @NonNull
    @Override
    public YoServiceTarget responsesTarget() {
        return target;
    }

    @Nullable
    @Override
    public YoServiceTarget messagesTarget() {
        return null;
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
        return "OpenAiPlatform{baseUrl=" + target.baseUrl() + ", apiKey=redacted}";
    }

    public static final class Builder {
        private String id = ID;
        private String baseUrl = "https://api.openai.com";
        private String apiKey;
        @Nullable
        private String organization;
        @Nullable
        private String project;

        /**
         * 同一个客户端里挂多台 OpenAI 兼容主机时，用不同的 id 区分。
         */
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
        public Builder organization(@Nullable String organization) {
            this.organization = organization;
            return this;
        }

        @NonNull
        public Builder project(@Nullable String project) {
            this.project = project;
            return this;
        }

        @NonNull
        public OpenAiPlatform build() {
            String key = YoServiceTarget.requireText(apiKey, "apiKey");
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "Bearer " + key);
            if (organization != null && !organization.isBlank()) {
                headers.put("OpenAI-Organization", organization.trim());
            }
            if (project != null && !project.isBlank()) {
                headers.put("OpenAI-Project", project.trim());
            }
            return new OpenAiPlatform(id, new YoServiceTarget(baseUrl, headers));
        }
    }
}
