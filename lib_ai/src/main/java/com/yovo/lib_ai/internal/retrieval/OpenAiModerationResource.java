package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.api.YoModeration;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.retrieval.YoModerationRequest;
import com.yovo.lib_ai.retrieval.YoModerationResult;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;

/** OpenAI {@code POST /v1/moderations}。 */
public final class OpenAiModerationResource implements YoModeration {

    public static final String PATH = "/v1/moderations";

    private final RetrievalHttp http;
    private final YoServiceTarget target;
    private final CallRegistry registry;

    public OpenAiModerationResource(
            @NonNull OkHttpClient client,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry) {
        this(new RetrievalHttp(client), target, registry);
    }

    public OpenAiModerationResource(
            @NonNull RetrievalHttp http,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry) {
        this.http = http;
        this.target = target;
        this.registry = registry;
    }

    @NonNull
    @Override
    public YoModerationResult moderate(@NonNull YoModerationRequest request) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            JSONObject body;
            try {
                body = ModerationCodec.encode(request);
            } catch (JSONException exception) {
                throw new YoAiProtocolException("Failed to encode moderation request", null, exception);
            }
            HttpExchange exchange = RetrievalCalls.json(http, target, "POST", PATH, body, token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                return ModerationCodec.decode(exchange.body() == null ? "" : exchange.body());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }
}
