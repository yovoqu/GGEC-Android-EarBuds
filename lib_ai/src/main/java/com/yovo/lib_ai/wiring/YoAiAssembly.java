package com.yovo.lib_ai.wiring;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.api.YoAiDiagnosticSource;
import com.yovo.lib_ai.api.YoAiDiagnostics;
import com.yovo.lib_ai.api.YoAnthropicMessages;
import com.yovo.lib_ai.api.YoBatch;
import com.yovo.lib_ai.api.YoChatCompletions;
import com.yovo.lib_ai.api.YoCompletions;
import com.yovo.lib_ai.api.YoEmbeddings;
import com.yovo.lib_ai.api.YoFiles;
import com.yovo.lib_ai.api.YoLatencyProbe;
import com.yovo.lib_ai.api.YoModeration;
import com.yovo.lib_ai.api.YoModels;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.api.YoRerank;
import com.yovo.lib_ai.api.YoResponses;
import com.yovo.lib_ai.api.YoTokenCounter;
import com.yovo.lib_ai.internal.common.diagnostics.YoAiDiagnosticsBus;
import com.yovo.lib_ai.internal.common.diagnostics.YoAiDiagnosticsInstaller;
import com.yovo.lib_ai.internal.common.diagnostics.YoAiLogger;
import com.yovo.lib_ai.internal.chat.ChatCompletionsResource;
import com.yovo.lib_ai.internal.completion.CompletionsResource;
import com.yovo.lib_ai.internal.messages.MessagesResource;
import com.yovo.lib_ai.internal.models.ModelDirectoryResource;
import com.yovo.lib_ai.internal.probe.LatencyProbeResource;
import com.yovo.lib_ai.internal.responses.ResponsesResource;
import com.yovo.lib_ai.internal.retrieval.AnthropicTokenCounterResource;
import com.yovo.lib_ai.internal.retrieval.BatchResource;
import com.yovo.lib_ai.internal.retrieval.FilesResource;
import com.yovo.lib_ai.internal.retrieval.OpenAiEmbeddingsResource;
import com.yovo.lib_ai.internal.retrieval.OpenAiModerationResource;
import com.yovo.lib_ai.internal.retrieval.OpenAiTokenCounterResource;
import com.yovo.lib_ai.internal.retrieval.QwenEmbeddingsResource;
import com.yovo.lib_ai.internal.retrieval.QwenRerankResource;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.OrderedCallbacks;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.media.YoImageServiceImpl;
import com.yovo.lib_ai.media.YoVideoServiceImpl;
import com.yovo.lib_ai.models.YoModelListing;
import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.platform.anthropic.AnthropicPlatform;
import com.yovo.lib_ai.platform.deepseek.DeepSeekPlatform;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;
import com.yovo.lib_ai.platform.qwen.QwenPlatform;
import com.yovo.lib_ai.speech.YoSpeechSynthesis;

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

    private static final String TAG = "YoAiAssembly";

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
        YoAiLogger.i(TAG, "opening YoAiClient: platforms=" + platforms.size());
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
        Map<String, YoEmbeddings> embeddings = new LinkedHashMap<>();
        Map<String, YoRerank> rerank = new LinkedHashMap<>();
        Map<String, YoFiles> files = new LinkedHashMap<>();
        Map<String, YoBatch> batch = new LinkedHashMap<>();
        Map<String, YoTokenCounter> tokenCounters = new LinkedHashMap<>();
        Map<String, YoModeration> moderation = new LinkedHashMap<>();

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
            if (!completion.protocols().isEmpty()) {
                completions.put(platform.id(), completion);
            }
            Protocols unretried = protocols(
                    platform, probeTransport, httpClient, network, ordered, registry, connectTimeoutMillis);
            probes.put(platform.id(), new LatencyProbeResource(
                    new CompletionsResource(unretried.chat, unretried.responses, unretried.messages),
                    network,
                    ordered,
                    registry));

            registerRetrievalServices(
                    platform,
                    httpClient,
                    registry,
                    embeddings,
                    rerank,
                    files,
                    batch,
                    tokenCounters,
                    moderation);
        }

        YoSpeechSynthesis synthesis = YoSpeechSynthesis.builder()
                .connectTimeoutMillis(connectTimeoutMillis)
                .readTimeoutMillis(Math.max(readTimeoutMillis, 120_000L))
                .writeTimeoutMillis(writeTimeoutMillis)
                .build();
        YoImageServiceImpl imageGeneration = YoImageServiceImpl.builder()
                .connectTimeoutMillis(connectTimeoutMillis)
                .readTimeoutMillis(Math.max(readTimeoutMillis, 120_000L))
                .writeTimeoutMillis(writeTimeoutMillis)
                .build();
        YoVideoServiceImpl videoGeneration = YoVideoServiceImpl.builder()
                .connectTimeoutMillis(connectTimeoutMillis)
                .readTimeoutMillis(Math.max(readTimeoutMillis, 120_000L))
                .writeTimeoutMillis(writeTimeoutMillis)
                .build();

        ExecutorService callbackToClose = ownedCallback;
        YoAiDiagnosticsInstaller.installSink();
        YoAiDiagnosticSource diagnosticSource = new YoAiDiagnosticSource() {
            @Override
            public java.util.List<String> timeline() {
                return YoAiDiagnosticsBus.get().timelineSnapshot();
            }

            @Override
            public String dumpReport() {
                return YoAiDiagnosticsBus.get().dumpReport();
            }
        };
        return new YoAiClient(
                chat,
                responses,
                messages,
                models,
                completions,
                probes,
                embeddings,
                rerank,
                files,
                batch,
                tokenCounters,
                moderation,
                synthesis,
                imageGeneration,
                videoGeneration,
                () -> {
                    YoAiLogger.i(TAG, "closing YoAiClient");
                    YoAiDiagnosticsBus.get().recordEvent("client.close", "YoAiClient");
                    synthesis.close();
                    imageGeneration.close();
                    videoGeneration.close();
                    shutdown(registry, network, httpClient, callbackToClose);
                }, diagnosticSource);
    }

    private static void registerRetrievalServices(
            YoPlatform platform,
            OkHttpClient httpClient,
            CallRegistry registry,
            Map<String, YoEmbeddings> embeddings,
            Map<String, YoRerank> rerank,
            Map<String, YoFiles> files,
            Map<String, YoBatch> batch,
            Map<String, YoTokenCounter> tokenCounters,
            Map<String, YoModeration> moderation) {
        YoServiceTarget target = platform.apiTarget();
        if (target == null) {
            return;
        }
        if (platform instanceof OpenAiPlatform) {
            embeddings.put(platform.id(), new OpenAiEmbeddingsResource(httpClient, target, registry));
            files.put(platform.id(), new FilesResource(httpClient, target, registry, FilesResource.Dialect.OPENAI));
            batch.put(platform.id(), new BatchResource(httpClient, target, registry, BatchResource.Dialect.OPENAI));
            tokenCounters.put(platform.id(), new OpenAiTokenCounterResource(httpClient, target, registry));
            moderation.put(platform.id(), new OpenAiModerationResource(httpClient, target, registry));
        } else if (platform instanceof QwenPlatform) {
            embeddings.put(platform.id(), new QwenEmbeddingsResource(httpClient, target, registry));
            rerank.put(platform.id(), new QwenRerankResource(httpClient, target, registry));
            files.put(platform.id(), new FilesResource(httpClient, target, registry, FilesResource.Dialect.QWEN));
            batch.put(platform.id(), new BatchResource(httpClient, target, registry, BatchResource.Dialect.QWEN));
        } else if (platform instanceof AnthropicPlatform) {
            files.put(platform.id(), new FilesResource(httpClient, target, registry, FilesResource.Dialect.ANTHROPIC));
            batch.put(platform.id(), new BatchResource(httpClient, target, registry, BatchResource.Dialect.ANTHROPIC));
            tokenCounters.put(platform.id(), new AnthropicTokenCounterResource(httpClient, target, registry));
        } else if (platform instanceof DeepSeekPlatform) {
            files.put(platform.id(), new FilesResource(httpClient, target, registry, FilesResource.Dialect.DEEPSEEK));
        }
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
        if (platform.protocols().contains(YoProtocol.CHAT_COMPLETIONS)) {
            protocols.chat = new ChatCompletionsResource(
                    transport,
                    requiredTarget(platform, YoProtocol.CHAT_COMPLETIONS, platform.chatTarget()),
                    platform.chatPartEncoder(),
                    platform.chatPolicy(),
                    network,
                    ordered,
                    registry);
        }
        if (platform.protocols().contains(YoProtocol.RESPONSES)) {
            protocols.responses = new ResponsesResource(
                    transport,
                    httpClient,
                    requiredTarget(platform, YoProtocol.RESPONSES, platform.responsesTarget()),
                    platform.responsesPolicy(),
                    network,
                    ordered,
                    registry,
                    connectTimeoutMillis);
        }
        if (platform.protocols().contains(YoProtocol.MESSAGES)) {
            protocols.messages = new MessagesResource(
                    transport,
                    requiredTarget(platform, YoProtocol.MESSAGES, platform.messagesTarget()),
                    platform.messagesPolicy(),
                    network,
                    ordered,
                    registry);
        }
        return protocols;
    }

    private static YoServiceTarget requiredTarget(
            YoPlatform platform, YoProtocol protocol, @Nullable YoServiceTarget target) {
        if (target == null) {
            throw new IllegalArgumentException(
                    platform.id() + " offers " + protocol.id() + " but has no target");
        }
        return target;
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
