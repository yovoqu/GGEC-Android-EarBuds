package com.yovo.lib_ai.wiring;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.api.YoAnthropicMessages;
import com.yovo.lib_ai.api.YoChatCompletions;
import com.yovo.lib_ai.api.YoCompletions;
import com.yovo.lib_ai.api.YoLatencyProbe;
import com.yovo.lib_ai.api.YoModels;
import com.yovo.lib_ai.api.YoResponses;
import com.yovo.lib_ai.internal.chat.ChatCompletionsResource;
import com.yovo.lib_ai.internal.completion.CompletionsResource;
import com.yovo.lib_ai.internal.messages.MessagesResource;
import com.yovo.lib_ai.internal.models.ModelDirectoryResource;
import com.yovo.lib_ai.internal.probe.LatencyProbeResource;
import com.yovo.lib_ai.internal.responses.ResponsesResource;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.OrderedCallbacks;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.models.YoModelListing;
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
        // 测速专用：共用同一个连接池，但从不重试，退避等待不能算进延迟。
        OkHttpTransport probeTransport = new OkHttpTransport(httpClient, 0, 0, 0, Thread::sleep);
        OrderedCallbacks ordered = new OrderedCallbacks(callbacks);
        Map<String, YoChatCompletions> chat = new LinkedHashMap<>();
        Map<String, YoResponses> responses = new LinkedHashMap<>();
        Map<String, YoAnthropicMessages> messages = new LinkedHashMap<>();
        Map<String, YoModels> models = new LinkedHashMap<>();
        Map<String, YoCompletions> completions = new LinkedHashMap<>();
        Map<String, YoLatencyProbe> probes = new LinkedHashMap<>();
        for (YoPlatform platform : platforms) {
            Protocols standard = protocols(
                    platform, transport, httpClient, network, ordered, registry, connectTimeoutMillis);
            if (standard.chat != null) {
                chat.put(platform.id(), standard.chat);
            }
            if (standard.responses != null) {
                responses.put(platform.id(), standard.responses);
            }
            if (standard.messages != null) {
                messages.put(platform.id(), standard.messages);
            }
            YoModelListing listing = platform.modelListing();
            if (listing != null) {
                models.put(platform.id(), new ModelDirectoryResource(transport, listing, registry));
            }
            CompletionsResource completion = new CompletionsResource(
                    standard.chat, standard.responses, standard.messages);
            if (completion.protocols().isEmpty()) {
                continue;
            }
            completions.put(platform.id(), completion);
            Protocols unretried = protocols(
                    platform, probeTransport, httpClient, network, ordered, registry, connectTimeoutMillis);
            probes.put(platform.id(), new LatencyProbeResource(
                    new CompletionsResource(unretried.chat, unretried.responses, unretried.messages),
                    network,
                    ordered,
                    registry));
        }
        ExecutorService callbackToClose = ownedCallback;
        return new YoAiClient(
                chat,
                responses,
                messages,
                models,
                completions,
                probes,
                () -> shutdown(registry, network, httpClient, callbackToClose));
    }

    private static Protocols protocols(
            YoPlatform platform,
            OkHttpTransport transport,
            OkHttpClient httpClient,
            Executor network,
            OrderedCallbacks ordered,
            CallRegistry registry,
            long connectTimeoutMillis) {
        Protocols protocols = new Protocols();
        if (platform.chatTarget() != null) {
            protocols.chat = new ChatCompletionsResource(
                    transport,
                    platform.chatTarget(),
                    platform.chatPartEncoder(),
                    platform.chatPolicy(),
                    network,
                    ordered,
                    registry);
        }
        if (platform.responsesTarget() != null) {
            protocols.responses = new ResponsesResource(
                    transport,
                    httpClient,
                    platform.responsesTarget(),
                    platform.responsesPolicy(),
                    network,
                    ordered,
                    registry,
                    connectTimeoutMillis);
        }
        if (platform.messagesTarget() != null) {
            protocols.messages = new MessagesResource(
                    transport,
                    platform.messagesTarget(),
                    platform.messagesPolicy(),
                    network,
                    ordered,
                    registry);
        }
        return protocols;
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

    /** 一个平台在某条传输上的三条标准协议资源，平台没提供的为空。 */
    private static final class Protocols {
        @Nullable
        YoChatCompletions chat;
        @Nullable
        YoResponses responses;
        @Nullable
        YoAnthropicMessages messages;
    }
}
