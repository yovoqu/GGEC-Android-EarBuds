package com.yovo.lib_ai.internal.transport;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiTransportException;
import com.yovo.lib_ai.internal.runtime.CancelToken;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class OkHttpTransport {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_ERROR_BODY_CHARS = 8192;

    private final OkHttpClient client;
    private final int maxRetries;
    private final long initialRetryDelayMillis;
    private final long maxRetryDelayMillis;
    private final Sleeper sleeper;

    public OkHttpTransport(
            OkHttpClient client,
            int maxRetries,
            long initialRetryDelayMillis,
            long maxRetryDelayMillis,
            Sleeper sleeper) {
        this.client = client;
        this.maxRetries = maxRetries;
        this.initialRetryDelayMillis = initialRetryDelayMillis;
        this.maxRetryDelayMillis = maxRetryDelayMillis;
        this.sleeper = sleeper;
    }

    public HttpExchange execute(HttpRequestSpec spec) {
        CancelToken token = spec.cancelToken();
        int attempt = 0;
        while (true) {
            throwIfCancelled(token);
            Call call = client.newCall(build(spec));
            token.onCancel(call::cancel);
            try {
                Response response = call.execute();
                int code = response.code();
                if (shouldRetry(code) && attempt < maxRetries) {
                    long delay = retryDelay(response, attempt);
                    response.close();
                    attempt++;
                    sleep(token, delay);
                    continue;
                }
                return toExchange(response, spec.stream());
            } catch (IOException exception) {
                if (token.isCancelled() || exception instanceof InterruptedIOException && token.isCancelled()) {
                    throw new YoAiTransportException("Call cancelled", exception);
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

    private Request build(HttpRequestSpec spec) {
        Request.Builder builder = new Request.Builder().url(spec.url());
        spec.headers().forEach(builder::header);
        RequestBody body = spec.body() == null ? null : RequestBody.create(spec.body(), JSON);
        builder.method(spec.method(), body);
        return builder.build();
    }

    private HttpExchange toExchange(Response response, boolean stream) throws IOException {
        String requestId = requestId(response);
        ResponseBody responseBody = response.body();
        if (stream && response.isSuccessful()) {
            if (responseBody == null) {
                response.close();
                throw new YoAiTransportException("Streaming response has no body", null);
            }
            return new HttpExchange(response.code(), requestId, null, responseBody.source(), response);
        }
        String body = "";
        if (responseBody != null) {
            body = response.isSuccessful()
                    ? responseBody.string()
                    : response.peekBody(MAX_ERROR_BODY_CHARS).string();
        }
        return new HttpExchange(response.code(), requestId, body, null, response);
    }

    @Nullable
    public static String requestId(Response response) {
        String id = response.header("x-request-id");
        if (id == null || id.isBlank()) {
            id = response.header("request-id");
        }
        return id == null || id.isBlank() ? null : id;
    }

    static boolean shouldRetry(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private long retryDelay(Response response, int attempt) {
        String retryAfterMs = response.header("retry-after-ms");
        if (retryAfterMs != null) {
            Long parsed = parseLong(retryAfterMs.trim());
            if (parsed != null) {
                return cap(parsed);
            }
        }
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null) {
            Long parsed = parseRetryAfter(retryAfter.trim());
            if (parsed != null) {
                return cap(parsed);
            }
        }
        return backoff(attempt);
    }

    private long backoff(int attempt) {
        long factor = 1L << Math.min(attempt, 10);
        long delay = initialRetryDelayMillis * factor;
        if (delay < 0) {
            return maxRetryDelayMillis;
        }
        return cap(delay);
    }

    private long cap(long delay) {
        if (delay < 0) {
            return 0;
        }
        return Math.min(delay, maxRetryDelayMillis);
    }

    @Nullable
    private static Long parseRetryAfter(String value) {
        Long seconds = parseLong(value);
        if (seconds != null) {
            return seconds * 1000L;
        }
        try {
            ZonedDateTime when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            long millis = Duration.between(ZonedDateTime.now(), when).toMillis();
            return Math.max(0L, millis);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static Long parseLong(String value) {
        if (value.isEmpty()) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void sleep(CancelToken token, long delayMillis) {
        long remaining = delayMillis;
        while (remaining > 0) {
            throwIfCancelled(token);
            long step = Math.min(remaining, 50L);
            try {
                sleeper.sleep(step);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new YoAiTransportException("Call cancelled", exception);
            }
            remaining -= step;
        }
        throwIfCancelled(token);
    }

    private static void throwIfCancelled(CancelToken token) {
        if (token.isCancelled()) {
            throw new YoAiTransportException("Call cancelled", null);
        }
    }

    public static OkHttpClient newClient(long connectTimeoutMillis, long readTimeoutMillis, long writeTimeoutMillis) {
        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    public static void shutdown(OkHttpClient client) {
        client.dispatcher().cancelAll();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public static Headers headersOf(java.util.Map<String, String> headers) {
        return Headers.of(headers);
    }
}
