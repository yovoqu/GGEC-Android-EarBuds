package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiTransportException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * {@link MediaTransport} 的 OkHttp 实现：媒体包里唯一接触 okhttp3 的类。
 */
final class OkHttpMediaTransport implements MediaTransport {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_ERROR_BODY_BYTES = 8192;

    private final OkHttpClient client;
    private final AtomicBoolean closed = new AtomicBoolean();

    OkHttpMediaTransport(long connectTimeoutMillis, long readTimeoutMillis, long writeTimeoutMillis) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    @NonNull
    @Override
    public MediaTransport.Response post(
            @NonNull String url,
            @NonNull Map<String, String> headers,
            @NonNull byte[] body,
            @NonNull String contentType) {
        MediaType mediaType = MediaType.get(contentType);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, mediaType));
        headers.forEach(builder::header);
        return execute(builder.build());
    }

    @NonNull
    @Override
    public MediaTransport.Response get(@NonNull String url, @NonNull Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(url).get();
        headers.forEach(builder::header);
        return execute(builder.build());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        client.dispatcher().cancelAll();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    @NonNull
    private MediaTransport.Response execute(@NonNull Request request) {
        Call call = client.newCall(request);
        try (okhttp3.Response response = call.execute()) {
            String requestId = requestId(response);
            String contentType = response.header("Content-Type");
            ResponseBody responseBody = response.body();
            byte[] body = new byte[0];
            if (responseBody != null) {
                byte[] raw = responseBody.bytes();
                if (response.isSuccessful()) {
                    body = raw;
                } else {
                    body = raw.length <= MAX_ERROR_BODY_BYTES
                            ? raw
                            : java.util.Arrays.copyOf(raw, MAX_ERROR_BODY_BYTES);
                }
            }
            return new MediaTransport.Response(response.code(), requestId, body, contentType);
        } catch (IOException exception) {
            if (closed.get()) {
                throw YoAiTransportException.cancelled(exception);
            }
            throw new YoAiTransportException(
                    exception.getMessage() == null ? "Media request failed" : exception.getMessage(),
                    exception);
        }
    }

    @Nullable
    private static String requestId(okhttp3.Response response) {
        String id = response.header("x-request-id");
        if (id == null || id.isBlank()) {
            id = response.header("request-id");
        }
        return id == null || id.isBlank() ? null : id;
    }

    @NonNull
    static MediaType json() {
        return JSON;
    }
}
