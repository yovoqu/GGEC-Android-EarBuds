package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.api.YoTokenCounter;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.retrieval.YoTokenCountRequest;
import com.yovo.lib_ai.retrieval.YoTokenCountResult;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;

/** OpenAI {@code POST /v1/responses/input_tokens}。 */
public final class OpenAiTokenCounterResource implements YoTokenCounter {

    public static final String PATH = "/v1/responses/input_tokens";

    private final RetrievalHttp http;
    private final YoServiceTarget target;
    private final CallRegistry registry;

    public OpenAiTokenCounterResource(
            @NonNull OkHttpClient client,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry) {
        this(new RetrievalHttp(client), target, registry);
    }

    public OpenAiTokenCounterResource(
            @NonNull RetrievalHttp http,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry) {
        this.http = http;
        this.target = target;
        this.registry = registry;
    }

    @NonNull
    @Override
    public YoTokenCountResult count(@NonNull YoTokenCountRequest request) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            JSONObject body;
            try {
                body = TokenCodec.encodeOpenAi(request);
            } catch (JSONException exception) {
                throw new YoAiProtocolException("Failed to encode token count request", null, exception);
            }
            HttpExchange exchange = RetrievalCalls.json(http, target, "POST", PATH, body, token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                return TokenCodec.decode(exchange.body() == null ? "" : exchange.body());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }
}
