package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.api.YoRerank;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.retrieval.YoRerankRequest;
import com.yovo.lib_ai.retrieval.YoRerankResult;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;

/** DashScope 原生 {@code POST /api/v1/services/rerank/text-rerank/text-rerank}。 */
public final class QwenRerankResource implements YoRerank {

    public static final String PATH = "/api/v1/services/rerank/text-rerank/text-rerank";

    private final RetrievalHttp http;
    private final YoServiceTarget target;
    private final CallRegistry registry;

    public QwenRerankResource(
            @NonNull OkHttpClient client,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry) {
        this(new RetrievalHttp(client), target, registry);
    }

    public QwenRerankResource(
            @NonNull RetrievalHttp http,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry) {
        this.http = http;
        this.target = target;
        this.registry = registry;
    }

    @NonNull
    @Override
    public YoRerankResult rerank(@NonNull YoRerankRequest request) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            JSONObject body;
            try {
                body = RerankCodec.encode(request);
            } catch (JSONException exception) {
                throw new YoAiProtocolException("Failed to encode rerank request", null, exception);
            }
            HttpExchange exchange = RetrievalCalls.json(http, target, "POST", PATH, body, token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                YoRerankResult result = RerankCodec.decode(exchange.body() == null ? "" : exchange.body());
                return RerankCodec.truncate(result, request.topN());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }
}
