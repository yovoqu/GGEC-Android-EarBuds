package com.yovo.lib_ai.internal.chat;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoChatCompletions;
import com.yovo.lib_ai.chat.YoChatChunk;
import com.yovo.lib_ai.chat.YoChatCompletion;
import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatStreamListener;
import com.yovo.lib_ai.endpoint.YoOpenAiEndpoint;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.protocol.WirePaths;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.runtime.OrderedCallbacks;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.internal.transport.HttpRequestSpec;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.internal.transport.SseDecoder;
import com.yovo.lib_ai.internal.transport.SseEvent;

import org.json.JSONException;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatCompletionsResource implements YoChatCompletions {

    private final OkHttpTransport transport;
    private final YoOpenAiEndpoint endpoint;
    private final Executor network;
    private final OrderedCallbacks callbacks;
    private final CallRegistry registry;

    public ChatCompletionsResource(
            OkHttpTransport transport,
            YoOpenAiEndpoint endpoint,
            Executor network,
            OrderedCallbacks callbacks,
            CallRegistry registry) {
        this.transport = transport;
        this.endpoint = endpoint;
        this.network = network;
        this.callbacks = callbacks;
        this.registry = registry;
    }

    @NonNull
    @Override
    public YoChatCompletion complete(@NonNull YoChatCompletionRequest request) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            HttpExchange exchange = transport.execute(spec(request, false, token));
            try {
                ProtocolSupport.requireSuccess(exchange);
                return ChatCodec.decodeCompletion(exchange.body() == null ? "" : exchange.body());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    @Override
    public YoAiCall stream(@NonNull YoChatCompletionRequest request, @NonNull YoChatStreamListener listener) {
        CancelToken token = new CancelToken();
        registry.register(token);
        AtomicBoolean settled = new AtomicBoolean();
        network.execute(() -> {
            try {
                read(request, listener, token, settled);
            } catch (RuntimeException exception) {
                ProtocolSupport.fail(callbacks, token, settled, listener::onFailure, ProtocolSupport.asAi(exception));
            } finally {
                registry.unregister(token);
            }
        });
        return token::cancel;
    }

    private void read(
            YoChatCompletionRequest request,
            YoChatStreamListener listener,
            CancelToken token,
            AtomicBoolean settled) {
        HttpExchange exchange = transport.execute(spec(request, true, token));
        try {
            if (exchange.statusCode() < 200 || exchange.statusCode() >= 300) {
                ProtocolSupport.fail(
                        callbacks,
                        token,
                        settled,
                        listener::onFailure,
                        ProtocolSupport.httpException(
                                exchange.statusCode(),
                                exchange.requestId(),
                                exchange.body()));
                return;
            }
            ChatAccumulator accumulator = new ChatAccumulator();
            AtomicBoolean sawDone = new AtomicBoolean();
            SseDecoder.read(exchange.source(), event -> onEvent(event, listener, token, accumulator, sawDone));
            if (token.isCancelled() || settled.get()) {
                return;
            }
            YoChatCompletion completion = accumulator.toCompletion();
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            callbacks.submit(() -> listener.onComplete(completion));
        } catch (Exception exception) {
            if (token.isCancelled()) {
                return;
            }
            ProtocolSupport.fail(callbacks, token, settled, listener::onFailure, ProtocolSupport.asAi(exception));
        } finally {
            exchange.close();
        }
    }

    private void onEvent(
            SseEvent event,
            YoChatStreamListener listener,
            CancelToken token,
            ChatAccumulator accumulator,
            AtomicBoolean sawDone) {
        if (token.isCancelled() || sawDone.get()) {
            return;
        }
        String data = event.data().trim();
        if ("[DONE]".equals(data)) {
            sawDone.set(true);
            return;
        }
        YoChatChunk chunk = ChatCodec.decodeChunk(data);
        accumulator.add(chunk);
        callbacks.submit(() -> {
            if (!token.isCancelled()) {
                listener.onChunk(chunk);
            }
        });
    }

    private HttpRequestSpec spec(YoChatCompletionRequest request, boolean stream, CancelToken token) {
        try {
            return new HttpRequestSpec(
                    "POST",
                    ProtocolSupport.url(endpoint.baseUrl(), WirePaths.CHAT_COMPLETIONS),
                    ProtocolSupport.jsonHeaders(endpoint.authHeaders(), stream),
                    ChatCodec.encode(request, stream).toString(),
                    stream,
                    token);
        } catch (JSONException exception) {
            throw new com.yovo.lib_ai.error.YoAiProtocolException("Failed to encode chat request", null, exception);
        }
    }
}
