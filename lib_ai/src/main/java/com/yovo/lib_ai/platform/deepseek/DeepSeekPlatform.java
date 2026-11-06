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
 *
 * <p>Chat 有两个主机：普通主机 {@code https://api.deepseek.com} 与 Beta 主机
 * {@code https://api.deepseek.com/beta}。前缀续写（{@code prefix:true}）只在 Beta 主机上
 * 生效，所以 Builder 允许把 Chat 目标切到 Beta，同时保持旧构造行为不变。</p>
 */
public final class DeepSeekPlatform implements YoPlatform {

    public static final String ID = "deepseek";
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_BETA_BASE_URL = "https://api.deepseek.com/beta";
    /** DeepSeek 提供 Chat 和 Anthropic 兼容的 Messages，没有 Responses。 */
    public static final List<YoProtocol> OFFERED = List.of(
            YoProtocol.CHAT_COMPLETIONS, YoProtocol.MESSAGES);

    private final String id;
    private final YoServiceTarget openAi;
    private final YoServiceTarget messages;
    private final String betaBaseUrl;

    private DeepSeekPlatform(
            String id,
            YoServiceTarget openAi,
            YoServiceTarget messages,
            String betaBaseUrl) {
        this.id = id;
        this.openAi = openAi;
        this.messages = messages;
        this.betaBaseUrl = betaBaseUrl;
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

    /**
     * DeepSeek 支持前缀续写：最后一条 assistant 消息可带 {@code prefix:true}。
     * 该能力需要 Chat 目标指向 Beta 主机（见 {@link Builder#chatUsesBeta(boolean)}）。
     */
    public boolean supportsPrefixCompletion() {
        return true;
    }

    /**
     * 当前配置下 Beta 主机地址（不论 Chat 是否已切到 Beta，都可读）。
     */
    @NonNull
    public String betaBaseUrl() {
        return betaBaseUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return "DeepSeekPlatform{baseUrl=" + openAi.baseUrl() + ", apiKey=redacted}";
    }

    public static final class Builder {
        private String id = ID;
        private String baseUrl = DEFAULT_BASE_URL;
        private boolean baseUrlSet;
        private String betaBaseUrl = DEFAULT_BETA_BASE_URL;
        private boolean betaBaseUrlSet;
        private boolean chatUsesBeta;
        private String anthropicBaseUrl = DEFAULT_BASE_URL + "/anthropic";
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

        /**
         * Beta 主机地址，默认 {@code https://api.deepseek.com/beta}。前缀续写走这台主机。
         */
        @NonNull
        public Builder betaBaseUrl(@NonNull String betaBaseUrl) {
            this.betaBaseUrl = betaBaseUrl;
            this.betaBaseUrlSet = true;
            return this;
        }

        /**
         * 把 Chat 目标切到 Beta 主机（前缀续写需要）。默认 false，保持旧行为。
         */
        @NonNull
        public Builder chatUsesBeta(boolean chatUsesBeta) {
            this.chatUsesBeta = chatUsesBeta;
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

            // 未显式给 beta 地址但改了 Chat 主机时，Beta 跟着新主机走 {base}/beta。
            String resolvedBetaUrl = betaBaseUrl;
            if (!betaBaseUrlSet && baseUrlSet) {
                resolvedBetaUrl = appendPath(baseUrl, "beta");
            }

            String chatUrl = chatUsesBeta ? resolvedBetaUrl : baseUrl;

            String messagesUrl = anthropicBaseUrlSet || !baseUrlSet
                    ? anthropicBaseUrl
                    : messagesHost(baseUrl);
            return new DeepSeekPlatform(
                    id,
                    new YoServiceTarget(chatUrl, headers),
                    AnthropicHeaders.target(messagesUrl, key, version, betas),
                    resolvedBetaUrl);
        }

        /**
         * 用户改了 Chat 主机时，Messages 跟着走 {@code {base}/anthropic}。
         * 只改 Chat、Messages 仍打官方，代理和兼容端点会对不上。
         */
        private static String messagesHost(String chatBase) {
            String base = trimTrailingSlashes(chatBase);
            if (base.toLowerCase(Locale.ROOT).endsWith("/anthropic")) {
                return base;
            }
            return base + "/anthropic";
        }

        private static String appendPath(String base, String path) {
            return trimTrailingSlashes(base) + "/" + path;
        }

        private static String trimTrailingSlashes(String value) {
            String base = value;
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base;
        }
    }
}
