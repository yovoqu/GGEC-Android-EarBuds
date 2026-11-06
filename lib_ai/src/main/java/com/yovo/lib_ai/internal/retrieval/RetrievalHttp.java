package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiTransportException;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 检索类服务共用的阻塞式 HTTP 执行器。
 *
 * <p>为什么不用现成的 {@code OkHttpTransport}：那套只接受 JSON 字符串体并强制
 * {@code application/json}，而 Files 需要 {@code multipart/form-data} 的原始字节与自定义 Content-Type。
 * 这里统一支持任意 {@link RequestBody}，并复刻同一套重试口径（408/429/5xx 与网络中断）。
 *
 * <p>取消与关闭通过 {@link CancelToken} 挂钩到 OkHttp 的 {@link Call#cancel()}，语义与其它服务一致。
 */
public final class RetrievalHttp {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_ERROR_BODY_CHARS = 8192;

    private final OkHttpClient client;
    private final int maxRetries;
    private final long initialRetryDelayMillis;
    private final long maxRetryDelayMillis;

    public RetrievalHttp(@NonNull OkHttpClient client) {
        this(client, 2, 200L, 5_000L);
    }

    public RetrievalHttp(
            @NonNull OkHttpClient client,
            int maxRetries,
            long initialRetryDelayMillis,
            long maxRetryDelayMillis) {
        this.client = client;
        this.maxRetries = Math.max(0, maxRetries);
        this.initialRetryDelayMillis = Math.max(0L, initialRetryDelayMillis);
        this.maxRetryDelayMillis = Math.max(0L, maxRetryDelayMillis);
    }

    @NonNull
    public static RequestBody jsonBody(@NonNull String json) {
        return RequestBody.create(json, JSON);
    }

    @NonNull
    public static RequestBody rawBody(@NonNull byte[] bytes, @NonNull String contentType) {
        return RequestBody.create(bytes, MediaType.get(contentType));
    }

    @NonNull
    public HttpExchange execute(
            @NonNull String method,
            @NonNull String url,
            @NonNull Map<String, String> headers,
            @Nullable RequestBody body,
            @NonNull CancelToken token) {
        int attempt = 0;
        while (true) {
            if (token.isCancelled()) {
                throw YoAiTransportException.cancelled(null);
            }
            Request.Builder builder = new Request.Builder().url(url);
            headers.forEach(builder::header);
            RequestBody effective = body;
            if (effective == null && requiresBody(method)) {
                effective = RequestBody.create(new byte[0], (MediaType) null);
            }
            builder.method(method, effective);
            Call call = client.newCall(builder.build());
            token.onCancel(call::cancel);
            try {
                Response response = call.execute();
                int code = response.code();
                if (shouldRetry(code) && attempt < maxRetries) {
                    response.close();
                    attempt++;
                    sleep(token, backoff(attempt - 1));
                    continue;
                }
                return toExchange(response);
            } catch (IOException exception) {
                if (token.isCancelled() || exception instanceof InterruptedIOException && token.isCancelled()) {
                    throw YoAiTransportException.cancelled(exception);
                }
                if (attempt < maxRetries && !token.isCancelled()) {
                    attempt++;
                    sleep(token, backoff(attempt - 1));
                    continue;
                }
                throw new YoAiTransportException(
                        exception.getMessage() == null ? "Request failed" : exception.getMessage(),
                        exception);
            }
        }
    }

    private HttpExchange toExchange(Response response) throws IOException {
        ResponseBody responseBody = response.body();
        String body = "";
        if (responseBody != null) {
            body = response.isSuccessful()
                    ? responseBody.string()
                    : response.peekBody(MAX_ERROR_BODY_CHARS).string();
        }
        return new HttpExchange(response.code(), requestId(response), body, null, response);
    }

    @Nullable
    private static String requestId(Response response) {
        String id = response.header("x-request-id");
        if (id == null || id.isBlank()) {
            id = response.header("request-id");
        }
        return id == null || id.isBlank() ? null : id;
    }

    private static boolean shouldRetry(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    /** OkHttp 要求 POST/PUT/PATCH 必须带请求体；没有内容时给一个空体。 */
    private static boolean requiresBody(@NonNull String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private long backoff(int attempt) {
        long factor = 1L << Math.min(attempt, 10);
        long delay = initialRetryDelayMillis * factor;
        if (delay < 0) {
            return maxRetryDelayMillis;
        }
        return Math.min(delay, maxRetryDelayMillis);
    }

    private void sleep(CancelToken token, long delayMillis) {
        long remaining = delayMillis;
        while (remaining > 0) {
            if (token.isCancelled()) {
                throw YoAiTransportException.cancelled(null);
            }
            long step = Math.min(remaining, 50L);
            try {
                Thread.sleep(step);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw YoAiTransportException.cancelled(exception);
            }
            remaining -= step;
        }
    }
}
