package com.yovo.lib_ai.internal.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoResponses;
import com.yovo.lib_ai.api.YoResponsesConnection;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.responses.YoResponsesPolicy;
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
import com.yovo.lib_ai.responses.YoResponseObject;
import com.yovo.lib_ai.responses.YoResponseRequest;
import com.yovo.lib_ai.responses.YoResponseStreamListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;

public final class ResponsesResource implements YoResponses {

    private final OkHttpTransport transport;
    private final OkHttpClient httpClient;
    private final YoServiceTarget target;
    private final YoResponsesPolicy policy;
    private final Executor network;
    private final OrderedCallbacks callbacks;
    private final CallRegistry registry;
    private final long connectTimeoutMillis;

    public ResponsesResource(
            OkHttpTransport transport,
            OkHttpClient httpClient,
            YoServiceTarget target,
            YoResponsesPolicy policy,
            Executor network,
            OrderedCallbacks callbacks,
            CallRegistry registry,
            long connectTimeoutMillis) {
        this.transport = transport;
        this.httpClient = httpClient;
        this.target = target;
        this.policy = policy;
        this.network = network;
        this.callbacks = callbacks;
        this.registry = registry;
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    @NonNull
    @Override
    public YoResponseObject create(@NonNull YoResponseRequest request) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            HttpExchange exchange = transport.execute(spec(request, false, token));
            try {
                ProtocolSupport.requireSuccess(exchange);
                return ResponsesCodec.decodeResponse(exchange.body() == null ? "" : exchange.body());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    @Override
    public YoAiCall stream(@NonNull YoResponseRequest request, @NonNull YoResponseStreamListener listener) {
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

    @NonNull
    @Override
    public YoResponsesConnection connect() {
        return ResponsesSession.open(httpClient, target, policy, callbacks, connectTimeoutMillis);
    }

    private void read(
            YoResponseRequest request,
            YoResponseStreamListener listener,
            CancelToken token,
            AtomicBoolean settled) {
        HttpExchange exchange = transport.execute(spec(request, true, token));
        ResponsesReducer reducer = new ResponsesReducer();
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
            SseDecoder.read(exchange.source(), event -> onEvent(event.data(), listener, token, reducer, settled));
            if (token.isCancelled() || settled.get()) {
                return;
            }
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            YoResponseObject response = reducer.toObject();
            callbacks.submit(() -> listener.onComplete(response));
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
            YoResponseStreamListener listener,
            CancelToken token,
            ResponsesReducer reducer,
            AtomicBoolean settled) {
        if (token.isCancelled() || settled.get() || data.isBlank()) {
            return;
        }
        try {
            JSONObject event = new JSONObject(data);
            reducer.apply(event);
            ResponsesTurn.Kind kind = ResponsesTurn.classify(event);
            callbacks.submit(() -> {
                if (!token.isCancelled()) {
                    listener.onEvent(ResponsesCodec.decodeEvent(event));
                }
            });
            if (kind == ResponsesTurn.Kind.CONTINUE) {
                return;
            }
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (kind == ResponsesTurn.Kind.FAIL) {
                callbacks.submit(() -> listener.onFailure(
                        new YoAiProtocolException(ResponsesTurn.errorMessage(event), event.toString())));
                return;
            }
            YoResponseObject response = reducer.toObject();
            callbacks.submit(() -> listener.onComplete(response));
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Invalid Responses event", data, exception);
        }
    }

    private HttpRequestSpec spec(YoResponseRequest request, boolean stream, CancelToken token) {
        policy.check(request);
        try {
            JSONObject body = ResponsesCodec.encodeHttp(request, stream);
            policy.apply(body);
            return new HttpRequestSpec(
                    "POST",
                    ProtocolSupport.url(target.baseUrl(), WirePaths.RESPONSES),
                    ProtocolSupport.jsonHeaders(target.headers(), stream),
                    body.toString(),
                    stream,
                    token);
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Failed to encode responses request", null, exception);
        }
    }
}
