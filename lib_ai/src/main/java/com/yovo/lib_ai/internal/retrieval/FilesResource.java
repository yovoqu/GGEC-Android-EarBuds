package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.api.YoFiles;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.retrieval.YoFileRef;
import com.yovo.lib_ai.retrieval.YoFileUploadRequest;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import okhttp3.OkHttpClient;

/**
 * 文件服务的阻塞实现。路径按厂商切换：OpenAI / Anthropic / DeepSeek 走 {@code /v1/files}，
 * DashScope 原生走 {@code /api/v1/files}。上传用 OkHttp 的 multipart 原始字节。
 */
public final class FilesResource implements YoFiles {

    /** 文件协议方言。 */
    public enum Dialect {
        OPENAI("/v1/files", false),
        ANTHROPIC("/v1/files", false),
        DEEPSEEK("/v1/files", false),
        QWEN("/api/v1/files", true);

        private final String path;
        private final boolean temporaryUrl;

        Dialect(String path, boolean temporaryUrl) {
            this.path = path;
            this.temporaryUrl = temporaryUrl;
        }

        @NonNull
        public String path() {
            return path;
        }
    }

    private final RetrievalHttp http;
    private final YoServiceTarget target;
    private final CallRegistry registry;
    private final Dialect dialect;

    public FilesResource(
            @NonNull OkHttpClient client,
            @NonNull YoServiceTarget target,
            @NonNull CallRegistry registry,
            @NonNull Dialect dialect) {
        this(new RetrievalHttp(client), target, registry, dialect);
    }

    public FilesResource(
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
    public YoFileRef upload(@NonNull YoFileUploadRequest request) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            MultipartForm form = MultipartForm.of(request);
            HttpExchange exchange = http.execute(
                    "POST",
                    ProtocolSupport.url(target.baseUrl(), dialect.path()),
                    RetrievalCalls.headers(target),
                    RetrievalHttp.rawBody(form.body, form.contentType()),
                    token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                String raw = exchange.body() == null ? "" : exchange.body();
                return FilesCodec.decodeFile(RetrievalJson.parseObject(raw, "File upload"), raw);
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    @Override
    public List<YoFileRef> list() {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            HttpExchange exchange = http.execute(
                    "GET",
                    ProtocolSupport.url(target.baseUrl(), dialect.path()),
                    RetrievalCalls.headers(target),
                    null,
                    token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                return FilesCodec.decodeList(exchange.body() == null ? "" : exchange.body());
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    @Override
    public YoFileRef get(@NonNull String fileId) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            HttpExchange exchange = http.execute(
                    "GET",
                    ProtocolSupport.url(target.baseUrl(), dialect.path() + "/" + encode(fileId)),
                    RetrievalCalls.headers(target),
                    null,
                    token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                String raw = exchange.body() == null ? "" : exchange.body();
                return FilesCodec.decodeFile(RetrievalJson.parseObject(raw, "File"), raw);
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @Override
    public boolean delete(@NonNull String fileId) {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            HttpExchange exchange = http.execute(
                    "DELETE",
                    ProtocolSupport.url(target.baseUrl(), dialect.path() + "/" + encode(fileId)),
                    RetrievalCalls.headers(target),
                    null,
                    token);
            try {
                // 删除幂等：文件已不存在视为成功。
                if (exchange.statusCode() == 404) {
                    return true;
                }
                ProtocolSupport.requireSuccess(exchange);
                return true;
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    @Override
    public String temporaryUrl(@NonNull String fileId) {
        if (!dialect.temporaryUrl) {
            throw new UnsupportedOperationException(
                    "Temporary file URL is not supported by " + dialect);
        }
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            HttpExchange exchange = http.execute(
                    "POST",
                    ProtocolSupport.url(target.baseUrl(), dialect.path() + "/" + encode(fileId) + "/temporary-url"),
                    RetrievalCalls.headers(target),
                    null,
                    token);
            try {
                ProtocolSupport.requireSuccess(exchange);
                String raw = exchange.body() == null ? "" : exchange.body();
                String url = FilesCodec.temporaryUrl(raw);
                if (url == null) {
                    throw new YoAiProtocolException("Temporary URL response has no url", raw);
                }
                return url;
            } finally {
                exchange.close();
            }
        } finally {
            registry.unregister(token);
        }
    }

    @NonNull
    private static String encode(@NonNull String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("fileId is required");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
