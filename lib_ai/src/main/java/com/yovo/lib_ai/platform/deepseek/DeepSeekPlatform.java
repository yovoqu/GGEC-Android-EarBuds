package com.yovo.lib_ai.platform.deepseek;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DeepSeek。Chat 文件块使用该主机的扁平形状，Messages 走 Anthropic 兼容路径。
 */
public final class DeepSeekPlatform implements YoPlatform {

    public static final String ID = "deepseek";
    /** DeepSeek 提供 Chat 和 Anthropic 兼容的 Messages，没有 Responses。 */
    public static final List<YoProtocol> OFFERED = List.of(
            YoProtocol.CHAT_COMPLETIONS, YoProtocol.MESSAGES);

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
    public Set<YoProtocol> protocols() {
        return Set.copyOf(OFFERED);
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
        private boolean baseUrlSet;
        private String anthropicBaseUrl = "https://api.deepseek.com/anthropic";
        private boolean anthropicBaseUrlSet;
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
            this.baseUrlSet = true;
            return this;
        }

        @NonNull
        public Builder anthropicBaseUrl(@NonNull String anthropicBaseUrl) {
            this.anthropicBaseUrl = anthropicBaseUrl;
            this.anthropicBaseUrlSet = true;
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
            String messagesUrl = anthropicBaseUrlSet || !baseUrlSet
                    ? anthropicBaseUrl
                    : messagesHost(baseUrl);
            return new DeepSeekPlatform(
                    id,
                    new YoServiceTarget(baseUrl, headers),
                    AnthropicHeaders.target(messagesUrl, key, version, betas));
        }

        /**
         * 用户改了 Chat 主机时，Messages 跟着走 {@code {base}/anthropic}。
         * 只改 Chat、Messages 仍打官方，代理和兼容端点会对不上。
         */
        private static String messagesHost(String chatBase) {
            String base = chatBase;
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            if (base.toLowerCase(Locale.ROOT).endsWith("/anthropic")) {
                return base;
            }
            return base + "/anthropic";
        }
    }
}
