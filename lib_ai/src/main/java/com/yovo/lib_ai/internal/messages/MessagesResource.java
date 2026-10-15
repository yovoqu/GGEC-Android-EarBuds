package com.yovo.lib_ai.internal.messages;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoAnthropicMessages;
import com.yovo.lib_ai.endpoint.YoAnthropicEndpoint;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.protocol.WirePaths;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.runtime.OrderedCallbacks;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.internal.transport.HttpRequestSpec;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.internal.transport.SseDecoder;
import com.yovo.lib_ai.messages.YoMessage;
import com.yovo.lib_ai.messages.YoMessageRequest;
import com.yovo.lib_ai.messages.YoMessageStreamListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MessagesResource implements YoAnthropicMessages {

    private final OkHttpTransport transport;
    private final YoAnthropicEndpoint endpoint;
    private final Executor network;
    private final OrderedCallbacks callbacks;
    private final CallRegistry registry;

    public MessagesResource(
            OkHttpTransport transport,
            YoAnthropicEndpoint endpoint,
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
    public YoMessage create(@NonNull YoMessageRequest request) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            HttpExchange exchange = transport.execute(spec(request, false, token));
            try {
                ProtocolSupport.requireSuccess(exchange);
                return MessagesCodec.decode(exchange.body() == null ? "" : exchange.body());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    @Override
    public YoAiCall stream(@NonNull YoMessageRequest request, @NonNull YoMessageStreamListener listener) {
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
            YoMessageRequest request,
            YoMessageStreamListener listener,
            CancelToken token,
            AtomicBoolean settled) {
        HttpExchange exchange = transport.execute(spec(request, true, token));
        MessageAccumulator accumulator = new MessageAccumulator();
        try {
            if (exchange.statusCode() < 200 || exchange.statusCode() >= 300) {
                ProtocolSupport.fail(
                        callbacks,
                        token,
                        settled,
                        listener::onFailure,
                        ProtocolSupport.httpException(exchange.statusCode(), exchange.requestId(), exchange.body()));
                return;
            }
            SseDecoder.read(exchange.source(), event -> onEvent(event.data(), listener, token, accumulator, settled));
            if (token.isCancelled() || settled.get()) {
                return;
            }
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            YoMessage message = accumulator.toMessage();
            callbacks.submit(() -> listener.onComplete(message));
        } catch (Exception exception) {
            if (!token.isCancelled()) {
                ProtocolSupport.fail(callbacks, token, settled, listener::onFailure, ProtocolSupport.asAi(exception));
            }
        } finally {
            exchange.close();
        }
    }

    private void onEvent(
            String data,
            YoMessageStreamListener listener,
            CancelToken token,
            MessageAccumulator accumulator,
            AtomicBoolean settled) {
        if (token.isCancelled() || settled.get() || data.isBlank()) {
            return;
        }
        try {
            JSONObject event = new JSONObject(data);
            accumulator.apply(event);
            if ("ping".equals(event.optString("type"))) {
                return;
            }
            callbacks.submit(() -> {
                if (!token.isCancelled()) {
                    listener.onEvent(MessagesCodec.decodeEvent(event));
                }
            });
            if (!accumulator.isStop() && !accumulator.isError()) {
                return;
            }
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (accumulator.isError()) {
                String message = accumulator.errorMessage();
                callbacks.submit(() -> listener.onFailure(new YoAiProtocolException(message, event.toString())));
                return;
            }
            YoMessage message = accumulator.toMessage();
            callbacks.submit(() -> listener.onComplete(message));
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Invalid Messages event", data, exception);
        }
    }

    private HttpRequestSpec spec(YoMessageRequest request, boolean stream, CancelToken token) {
        try {
            return new HttpRequestSpec(
                    "POST",
                    ProtocolSupport.url(endpoint.baseUrl(), WirePaths.MESSAGES),
                    ProtocolSupport.jsonHeaders(endpoint.authHeaders(), stream),
                    MessagesCodec.encode(request, stream).toString(),
                    stream,
                    token);
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Failed to encode messages request", null, exception);
        }
    }
}
