package com.yovo.lib_ai.speech;

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
 * {@link SynthesisTransport} 的 OkHttp 实现：唯一接触 okhttp3 的合成类。
 *
 * <p>成功时读完整字节（音频二进制原样保留，SSE 文本也是字节）；失败时只截断读取错误体。
 * 不在这里做重试：合成是"一次请求一个结果"的语义，重试策略留给上层，避免两个地方各自演化。
 */
final class OkHttpSynthesisTransport implements SynthesisTransport {

    private static final int MAX_ERROR_BODY_BYTES = 8192;

    private final OkHttpClient client;
    private final AtomicBoolean closed = new AtomicBoolean();

    OkHttpSynthesisTransport(long connectTimeoutMillis, long readTimeoutMillis, long writeTimeoutMillis) {
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
    public SynthesisTransport.Response post(
            @NonNull String url,
            @NonNull Map<String, String> headers,
            @NonNull byte[] body,
            @NonNull String contentType) {
        MediaType mediaType = MediaType.get(contentType);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, mediaType));
        headers.forEach(builder::header);
        Call call = client.newCall(builder.build());
        try (okhttp3.Response response = call.execute()) {
            return toResponse(response);
        } catch (IOException exception) {
            if (closed.get()) {
                throw YoAiTransportException.cancelled(exception);
            }
            throw new YoAiTransportException(
                    exception.getMessage() == null ? "Speech synthesis request failed" : exception.getMessage(),
                    exception);
        }
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

    private static SynthesisTransport.Response toResponse(okhttp3.Response response) throws IOException {
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
        return new SynthesisTransport.Response(response.code(), requestId, body, contentType);
    }

    @Nullable
    private static String requestId(okhttp3.Response response) {
        String id = response.header("x-request-id");
        if (id == null || id.isBlank()) {
            id = response.header("request-id");
        }
        return id == null || id.isBlank() ? null : id;
    }
}
