package com.yovo.lib_ai.wiring;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.api.YoAnthropicMessages;
import com.yovo.lib_ai.api.YoChatCompletions;
import com.yovo.lib_ai.api.YoResponses;
import com.yovo.lib_ai.internal.chat.ChatCompletionsResource;
import com.yovo.lib_ai.internal.messages.MessagesResource;
import com.yovo.lib_ai.internal.responses.ResponsesResource;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.OrderedCallbacks;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.platform.YoPlatform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            List<YoPlatform> platforms,
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
        Map<String, YoChatCompletions> chat = new LinkedHashMap<>();
        Map<String, YoResponses> responses = new LinkedHashMap<>();
        Map<String, YoAnthropicMessages> messages = new LinkedHashMap<>();
        for (YoPlatform platform : platforms) {
            if (platform.chatTarget() != null) {
                chat.put(platform.id(), new ChatCompletionsResource(
                        transport,
                        platform.chatTarget(),
                        platform.chatPartEncoder(),
                        platform.chatPolicy(),
                        network,
                        ordered,
                        registry));
            }
            if (platform.responsesTarget() != null) {
                responses.put(platform.id(), new ResponsesResource(
                        transport,
                        httpClient,
                        platform.responsesTarget(),
                        platform.responsesPolicy(),
                        network,
                        ordered,
                        registry,
                        connectTimeoutMillis));
            }
            if (platform.messagesTarget() != null) {
                messages.put(platform.id(), new MessagesResource(
                        transport,
                        platform.messagesTarget(),
                        platform.messagesPolicy(),
                        network,
                        ordered,
                        registry));
            }
        }
        ExecutorService callbackToClose = ownedCallback;
        return new YoAiClient(chat, responses, messages, () -> shutdown(registry, network, httpClient, callbackToClose));
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
