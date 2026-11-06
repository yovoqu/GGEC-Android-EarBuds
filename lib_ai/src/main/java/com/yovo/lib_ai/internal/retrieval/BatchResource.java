package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoBatch;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.retrieval.YoBatchJob;
import com.yovo.lib_ai.retrieval.YoBatchRequest;
import com.yovo.lib_ai.retrieval.YoBatchResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import okhttp3.OkHttpClient;

/**
 * 批量任务的阻塞实现。OpenAI / Qwen 走 {@code /v1/batches}，Anthropic 走 {@code /v1/messages/batches}。
 * 结果下载：OpenAI 先查任务拿到 {@code output_file_id}，再取文件内容（JSONL）；
 * Anthropic 直接用 {@code results_url}。
 */
public final class BatchResource implements YoBatch {

    /** 批量协议方言。 */
    public enum Dialect {
        OPENAI("/v1/batches", false),
        ANTHROPIC("/v1/messages/batches", true),
        QWEN("/v1/batches", false);

        private final String path;
        private final boolean anthropic;

        Dialect(String path, boolean anthropic) {
            this.path = path;
            this.anthropic = anthropic;
        }

        @NonNull
        public String path() {
            return path;
        }

        public boolean anthropic() {
            return anthropic;
        }
    }

    private final RetrievalHttp http;
    private final YoServiceTarget target;
    private final CallRegistry registry;
    private final Dialect dialect;

    public BatchResource(
            @NonNull OkHttpClient client,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry,
            @NonNull Dialect dialect) {
        this(new RetrievalHttp(client), target, registry, dialect);
    }

    public BatchResource(
            @NonNull RetrievalHttp http,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry,
            @NonNull Dialect dialect) {
        this.http = http;
        this.target = target;
        this.registry = registry;
        this.dialect = dialect;
    }

    @NonNull
    @Override
    public YoBatchJob submit(@NonNull YoBatchRequest request) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            JSONObject body;
            try {
                body = BatchCodec.encodeSubmit(request, dialect.anthropic());
            } catch (JSONException exception) {
                throw new YoAiProtocolException("Failed to encode batch request", null, exception);
            }
            HttpExchange exchange = RetrievalCalls.json(http, target, "POST", dialect.path(), body, token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                return BatchCodec.decodeJob(body(exchange), dialect.anthropic());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    @Override
    public YoBatchJob retrieve(@NonNull String batchId) {
        return get(batchId, "");
    }

    @NonNull
    @Override
    public YoBatchJob cancel(@NonNull String batchId) {
        return get(batchId, "/cancel");
    }

    @NonNull
    private YoBatchJob get(@NonNull String batchId, @NonNull String suffix) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            HttpExchange exchange = RetrievalCalls.json(
                    http, target, suffix.isEmpty() ? "GET" : "POST",
                    dialect.path() + "/" + encode(batchId) + suffix, null, token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                return BatchCodec.decodeJob(body(exchange), dialect.anthropic());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    @Override
    public List<YoBatchJob> list() {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            HttpExchange exchange = RetrievalCalls.json(
                    http, target, "GET", dialect.path(), null, token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                return BatchCodec.decodeList(body(exchange), dialect.anthropic());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    @Override
    public List<YoBatchResult> results(@NonNull String batchId) {
        YoBatchJob job = retrieve(batchId);
        String jsonl;
        if (job.outputFileId() != null) {
            jsonl = fetch("/v1/files/" + encode(job.outputFileId()) + "/content");
        } else if (job.resultsUrl() != null) {
            jsonl = fetch(job.resultsUrl());
        } else {
            jsonl = fetch(dialect.path() + "/" + encode(batchId) + "/results");
        }
        return BatchCodec.decodeResultsJsonl(jsonl);
    }

    @NonNull
    private String fetch(@NonNull String pathOrUrl) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            String url = pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")
                    ? pathOrUrl
                    : ProtocolSupport.url(target.baseUrl(), pathOrUrl);
            HttpExchange exchange = http.execute("GET", url, RetrievalCalls.headers(target), null, token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                return body(exchange);
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    private static String body(@NonNull HttpExchange exchange) {
        return exchange.body() == null ? "" : exchange.body();
    }

    @NonNull
    private static String encode(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("batchId is required");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
