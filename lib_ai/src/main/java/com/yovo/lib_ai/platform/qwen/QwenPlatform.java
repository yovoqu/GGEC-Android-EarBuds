package com.yovo.lib_ai.platform.qwen;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 阿里云百炼 DashScope。
 *
 * <p>DashScope 有两条主机前缀，混用会 404：</p>
 * <ul>
 *   <li>OpenAI 兼容的对话接口：base 到 {@code /compatible-mode/v1}，endpoint
 *       {@code /chat/completions}，拼出 {@code .../compatible-mode/v1/chat/completions}；
 *       这就是 {@link #chatTarget()}。</li>
 *   <li>检索/媒体等原生接口：{@code /api/v1/...}，主机为 {@code https://dashscope.aliyuncs.com}，
 *       即 {@link #apiTarget()}。</li>
 * </ul>
 *
 * <p>依据：百炼「OpenAI 兼容」文档的 base_url 与 endpoint 说明。</p>
 */
public final class QwenPlatform implements YoPlatform {

    public static final String ID = "qwen";
    /** OpenAI 兼容对话主机（含 /compatible-mode/v1 前缀）。 */
    public static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    /** 检索/媒体等原生接口的主机（裸 host，路径由各服务追加 /api/v1/...）。 */
    public static final String DEFAULT_API_BASE_URL = "https://dashscope.aliyuncs.com";
    /** 默认只声明 Chat；Responses/Messages 由 Builder 显式开启。 */
    public static final Set<YoProtocol> DEFAULT_PROTOCOLS = Set.of(YoProtocol.CHAT_COMPLETIONS);

    private final String id;
    private final Set<YoProtocol> protocols;
    private final YoServiceTarget target;
    private final YoServiceTarget apiTarget;

    private QwenPlatform(
            String id,
            Set<YoProtocol> protocols,
            YoServiceTarget target,
            YoServiceTarget apiTarget) {
        this.id = id;
        this.protocols = Set.copyOf(protocols);
        this.target = target;
        this.apiTarget = apiTarget;
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
        return protocols;
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

    @Nullable
    @Override
    public YoServiceTarget responsesTarget() {
        return protocols.contains(YoProtocol.RESPONSES) ? target : null;
    }

    @Nullable
    @Override
    public YoServiceTarget messagesTarget() {
        return protocols.contains(YoProtocol.MESSAGES) ? target : null;
    }

    /** 检索/媒体走裸 host（{@code /api/v1/...}），不是对话的 /compatible-mode/v1。 */
    @NonNull
    @Override
    public YoServiceTarget apiTarget() {
        return apiTarget;
    }

    @NonNull
    @Override
    public YoChatPartEncoder chatPartEncoder() {
        return OpenAiChatPartEncoder.INSTANCE;
    }

    @NonNull
    @Override
    public YoChatRequestPolicy chatPolicy() {
        return QwenChatPolicy.INSTANCE;
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
        return "QwenPlatform{baseUrl=" + target.baseUrl() + ", apiBaseUrl=" + apiTarget.baseUrl()
                + ", apiKey=redacted}";
    }

    public static final class Builder {
        private String id = ID;
        private String baseUrl = DEFAULT_BASE_URL;
        private String apiBaseUrl = DEFAULT_API_BASE_URL;
        private String apiKey;
        private boolean responses;
        private boolean messages;

        /**
         * 同一个客户端里挂多台 Qwen 兼容主机时，用不同的 id 区分。
         */
        @NonNull
        public Builder id(@NonNull String id) {
            this.id = YoServiceTarget.requireText(id, "id");
            return this;
        }

        /** OpenAI 兼容对话主机；默认到 {@code /compatible-mode/v1}。 */
        @NonNull
        public Builder baseUrl(@NonNull String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** 检索/媒体原生接口主机；默认裸 host {@code https://dashscope.aliyuncs.com}。 */
        @NonNull
        public Builder apiBaseUrl(@NonNull String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
            return this;
        }

        @NonNull
        public Builder apiKey(@NonNull String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** 额外声明 Responses（仅当部署确实提供该入口时开启）。 */
        @NonNull
        public Builder offerResponses() {
            this.responses = true;
            return this;
        }

        /** 额外声明 Anthropic 兼容的 Messages（仅当部署确实提供该入口时开启）。 */
        @NonNull
        public Builder offerMessages() {
            this.messages = true;
            return this;
        }

        @NonNull
        public QwenPlatform build() {
            String key = YoServiceTarget.requireText(apiKey, "apiKey");
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "Bearer " + key);
            Set<YoProtocol> offered = new LinkedHashSet<>();
            offered.add(YoProtocol.CHAT_COMPLETIONS);
            if (responses) {
                offered.add(YoProtocol.RESPONSES);
            }
            if (messages) {
                offered.add(YoProtocol.MESSAGES);
            }
            return new QwenPlatform(
                    id,
                    offered,
                    new YoServiceTarget(baseUrl, headers),
                    new YoServiceTarget(apiBaseUrl, headers));
        }
    }
}
