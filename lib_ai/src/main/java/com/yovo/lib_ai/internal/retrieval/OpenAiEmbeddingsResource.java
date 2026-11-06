package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.api.YoEmbeddings;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.retrieval.YoEmbeddingRequest;
import com.yovo.lib_ai.retrieval.YoEmbeddingResult;

import okhttp3.OkHttpClient;

/** OpenAI 兼容 {@code POST /v1/embeddings}。 */
public final class OpenAiEmbeddingsResource implements YoEmbeddings {

    public static final String PATH = "/v1/embeddings";

    private final RetrievalHttp http;
    private final com.yovo.lib_ai.platform.YoServiceTarget target;
    private final CallRegistry registry;

    public OpenAiEmbeddingsResource(
            @NonNull OkHttpClient client,
            @NonNull com.yovo.lib_ai.platform.YoServiceTarget target,
            @NonNull CallRegistry registry) {
        this(new RetrievalHttp(client), target, registry);
    }

    public OpenAiEmbeddingsResource(
            @NonNull RetrievalHttp http,
            @NonNull com.yovo.lib_ai.platform.YoServiceTarget target,
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
                body = EmbeddingCodec.encodeOpenAi(request);
            } catch (org.json.JSONException exception) {
                throw new com.yovo.lib_ai.error.YoAiProtocolException(
                        "Failed to encode embedding request", null, exception);
            }
            HttpExchange exchange = RetrievalCalls.json(http, target, "POST", PATH, body, token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                return EmbeddingCodec.decodeOpenAi(exchange.body() == null ? "" : exchange.body());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }
}
