package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.api.YoEmbeddings;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.retrieval.YoEmbeddingRequest;
import com.yovo.lib_ai.retrieval.YoEmbeddingResult;

import org.json.JSONException;

import okhttp3.OkHttpClient;

/** DashScope 原生 {@code POST /api/v1/services/embeddings/text-embedding/text-embedding}。 */
public final class QwenEmbeddingsResource implements YoEmbeddings {

    public static final String PATH = "/api/v1/services/embeddings/text-embedding/text-embedding";

    private final RetrievalHttp http;
    private final YoServiceTarget target;
    private final CallRegistry registry;

    public QwenEmbeddingsResource(
            @NonNull OkHttpClient client,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry) {
        this(new RetrievalHttp(client), target, registry);
    }

    public QwenEmbeddingsResource(
            @NonNull RetrievalHttp http,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry) {
        this.http = http;
        this.target = target;
        this.registry = registry;
    }

    @NonNull
    @Override
    public YoEmbeddingResult embed(@NonNull YoEmbeddingRequest request) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            org.json.JSONObject body;
            try {
                body = EmbeddingCodec.encodeQwen(request);
            } catch (JSONException exception) {
                throw new com.yovo.lib_ai.error.YoAiProtocolException(
                        "Failed to encode embedding request", null, exception);
            }
            HttpExchange exchange = RetrievalCalls.json(http, target, "POST", PATH, body, token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                return EmbeddingCodec.decodeQwen(exchange.body() == null ? "" : exchange.body());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }
}
