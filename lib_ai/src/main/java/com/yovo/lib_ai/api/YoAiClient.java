package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.endpoint.YoAnthropicEndpoint;
import com.yovo.lib_ai.endpoint.YoOpenAiEndpoint;
import com.yovo.lib_ai.wiring.YoAiAssembly;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI Chat Completions、OpenAI Responses 和 Anthropic Messages 的调用入口。
 * 一个客户端复用连接池。各资源上的一次性调用在调用线程阻塞，不要在主线程调用。
 */
public final class YoAiClient implements AutoCloseable {

    @Nullable
    private final YoChatCompletions chat;
    @Nullable
    private final YoResponses responses;
    @Nullable
    private final YoAnthropicMessages messages;
    private final Closer closer;
    private final AtomicBoolean closed = new AtomicBoolean();

    public YoAiClient(
            @Nullable YoChatCompletions chat,
            @Nullable YoResponses responses,
            @Nullable YoAnthropicMessages messages,
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
        ensureOpen();
        if (chat == null) {
            throw new IllegalStateException("OpenAI endpoint is not configured");
        }
        return chat;
    }

    @NonNull
    public YoResponses responses() {
        ensureOpen();
        if (responses == null) {
            throw new IllegalStateException("OpenAI endpoint is not configured");
        }
        return responses;
    }

    @NonNull
    public YoAnthropicMessages messages() {
        ensureOpen();
        if (messages == null) {
            throw new IllegalStateException("Anthropic endpoint is not configured");
        }
        return messages;
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

    /**
     * 关闭客户端持有的连接池和线程。由装配根实现。
     */
    public interface Closer {
        void close();
    }

    public static final class Builder {
        private YoOpenAiEndpoint openAi;
        private YoAnthropicEndpoint anthropic;
        private int maxRetries = 2;
        private long initialRetryDelayMillis = 200;
        private long maxRetryDelayMillis = 60_000;
        private long connectTimeoutMillis = 10_000;
        private long readTimeoutMillis = 60_000;
        private long writeTimeoutMillis = 30_000;
        @Nullable
        private Executor callbackExecutor;

        @NonNull
        public Builder openAiEndpoint(@NonNull YoOpenAiEndpoint endpoint) {
            this.openAi = endpoint;
            return this;
        }

        @NonNull
        public Builder anthropicEndpoint(@NonNull YoAnthropicEndpoint endpoint) {
            this.anthropic = endpoint;
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
            if (openAi == null && anthropic == null) {
                throw new IllegalArgumentException("At least one endpoint is required");
            }
            return YoAiAssembly.open(
                    openAi,
                    anthropic,
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
