package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.wiring.YoAiAssembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 按平台绑定调用 Chat Completions、Responses 和 Messages。
 * 一个客户端复用连接池。一次性调用在调用线程阻塞，不要在主线程调用。
 */
public final class YoAiClient implements AutoCloseable {

    private final Map<String, YoChatCompletions> chat;
    private final Map<String, YoResponses> responses;
    private final Map<String, YoAnthropicMessages> messages;
    private final Closer closer;
    private final AtomicBoolean closed = new AtomicBoolean();

    public YoAiClient(
            @NonNull Map<String, YoChatCompletions> chat,
            @NonNull Map<String, YoResponses> responses,
            @NonNull Map<String, YoAnthropicMessages> messages,
            @NonNull Closer closer) {
        this.chat = chat;
        this.responses = responses;
        this.messages = messages;
        this.closer = closer;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public YoChatCompletions chatCompletions() {
        return only(chat, "chat completions");
    }

    @NonNull
    public YoChatCompletions chatCompletions(@NonNull String platformId) {
        return require(chat, platformId, "chat completions");
    }

    @NonNull
    public YoResponses responses() {
        return only(responses, "responses");
    }

    @NonNull
    public YoResponses responses(@NonNull String platformId) {
        return require(responses, platformId, "responses");
    }

    @NonNull
    public YoAnthropicMessages messages() {
        return only(messages, "messages");
    }

    @NonNull
    public YoAnthropicMessages messages(@NonNull String platformId) {
        return require(messages, platformId, "messages");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closer.close();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("YoAiClient is closed");
        }
    }

    private <T> T only(Map<String, T> resources, String name) {
        ensureOpen();
        if (resources.isEmpty()) {
            throw new IllegalStateException("No platform provides " + name);
        }
        if (resources.size() > 1) {
            throw new IllegalStateException("Select a platform for " + name);
        }
        return resources.values().iterator().next();
    }

    private <T> T require(Map<String, T> resources, String platformId, String name) {
        ensureOpen();
        T resource = resources.get(platformId);
        if (resource == null) {
            throw new IllegalStateException("Platform " + platformId + " does not provide " + name);
        }
        return resource;
    }

    public interface Closer {
        void close();
    }

    public static final class Builder {
        private final List<YoPlatform> platforms = new ArrayList<>();
        private int maxRetries = 2;
        private long initialRetryDelayMillis = 200;
        private long maxRetryDelayMillis = 60_000;
        private long connectTimeoutMillis = 10_000;
        private long readTimeoutMillis = 60_000;
        private long writeTimeoutMillis = 30_000;
        @Nullable
        private Executor callbackExecutor;

        @NonNull
        public Builder platform(@NonNull YoPlatform platform) {
            for (YoPlatform existing : platforms) {
                if (existing.id().equals(platform.id())) {
                    throw new IllegalArgumentException("Platform is already configured: " + platform.id());
                }
            }
            platforms.add(platform);
            return this;
        }

        @NonNull
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        @NonNull
        public Builder initialRetryDelayMillis(long initialRetryDelayMillis) {
            if (initialRetryDelayMillis < 0) {
                throw new IllegalArgumentException("initialRetryDelayMillis must be >= 0");
            }
            this.initialRetryDelayMillis = initialRetryDelayMillis;
            return this;
        }

        @NonNull
        public Builder callbackExecutor(@NonNull Executor callbackExecutor) {
            this.callbackExecutor = callbackExecutor;
            return this;
        }

        @NonNull
        public YoAiClient build() {
            if (platforms.isEmpty()) {
                throw new IllegalArgumentException("At least one platform is required");
            }
            return YoAiAssembly.open(
                    List.copyOf(platforms),
                    maxRetries,
                    initialRetryDelayMillis,
                    maxRetryDelayMillis,
                    connectTimeoutMillis,
                    readTimeoutMillis,
                    writeTimeoutMillis,
                    callbackExecutor);
        }
    }
}
