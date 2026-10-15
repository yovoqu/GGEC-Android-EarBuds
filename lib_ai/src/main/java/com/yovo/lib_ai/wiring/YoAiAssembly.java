package com.yovo.lib_ai.wiring;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.endpoint.YoAnthropicEndpoint;
import com.yovo.lib_ai.endpoint.YoOpenAiEndpoint;
import com.yovo.lib_ai.internal.chat.ChatCompletionsResource;
import com.yovo.lib_ai.internal.messages.MessagesResource;
import com.yovo.lib_ai.internal.responses.ResponsesResource;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.OrderedCallbacks;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * lib_ai 的装配根。公开门面只拿到资源接口，不碰传输实现。
 */
public final class YoAiAssembly {

    private YoAiAssembly() {
    }

    public static YoAiClient open(
            @Nullable YoOpenAiEndpoint openAi,
            @Nullable YoAnthropicEndpoint anthropic,
            int maxRetries,
            long initialRetryDelayMillis,
            long maxRetryDelayMillis,
            long connectTimeoutMillis,
            long readTimeoutMillis,
            long writeTimeoutMillis,
            @Nullable Executor callbackExecutor) {
        OkHttpClient httpClient = OkHttpTransport.newClient(
                connectTimeoutMillis,
                readTimeoutMillis,
                writeTimeoutMillis);
        ExecutorService network = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "lib_ai-http");
            thread.setDaemon(true);
            return thread;
        });
        ExecutorService ownedCallback = null;
        Executor callbacks = callbackExecutor;
        if (callbacks == null) {
            ownedCallback = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "lib_ai-callback");
                thread.setDaemon(true);
                return thread;
            });
            callbacks = ownedCallback;
        }
        CallRegistry registry = new CallRegistry();
        OkHttpTransport transport = new OkHttpTransport(
                httpClient,
                maxRetries,
                initialRetryDelayMillis,
                maxRetryDelayMillis,
                Thread::sleep);
        OrderedCallbacks ordered = new OrderedCallbacks(callbacks);
        ExecutorService callbackToClose = ownedCallback;
        return new YoAiClient(
                openAi == null ? null : new ChatCompletionsResource(transport, openAi, network, ordered, registry),
                openAi == null ? null : new ResponsesResource(
                        transport,
                        httpClient,
                        openAi,
                        network,
                        ordered,
                        registry,
                        connectTimeoutMillis),
                anthropic == null ? null : new MessagesResource(transport, anthropic, network, ordered, registry),
                () -> shutdown(registry, network, httpClient, callbackToClose));
    }

    private static void shutdown(
            CallRegistry registry,
            ExecutorService network,
            OkHttpClient httpClient,
            @Nullable ExecutorService ownedCallback) {
        registry.cancelAll();
        network.shutdownNow();
        OkHttpTransport.shutdown(httpClient);
        if (ownedCallback != null) {
            ownedCallback.shutdown();
        }
        try {
            network.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
