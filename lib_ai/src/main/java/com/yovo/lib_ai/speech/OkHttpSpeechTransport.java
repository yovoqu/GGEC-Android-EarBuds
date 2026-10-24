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
 * {@link SpeechTransport} 的 OkHttp 实现：唯一接触 okhttp3 的类。
 *
 * <p>为什么不复用 {@code internal.transport.OkHttpTransport}：它的请求体是 {@code String}
 * 且把媒体类型写死成 JSON，multipart 无法经它发出。这里刻意只做"发送"这一件事，
 * 重试与错误分类留在 {@link YoWhisperSpeech}，避免两套重试逻辑各自演化。
 *
 * <p>超时是"两段数据之间允许的最长间隔"，不是整次请求的总时长：一段长音频的上传
 * 需要时间，网关处理也需要时间，用 callTimeout 卡总时长会把正常的长录音误判成超时。
 *
 * <p>注意：本类实现了 {@link SpeechTransport}，其嵌套类型 {@code SpeechTransport.Response}
 * 在本类体内是"继承来的成员类型"，会遮蔽同名的 import。因此 OkHttp 的
 * {@code okhttp3.Response} 一律写全限定名，避免两者混用。
 */
final class OkHttpSpeechTransport implements SpeechTransport {

    private static final int MAX_ERROR_BODY_CHARS = 8192;

    private final OkHttpClient client;
    private final AtomicBoolean closed = new AtomicBoolean();

    OkHttpSpeechTransport(long connectTimeoutMillis, long readTimeoutMillis, long writeTimeoutMillis) {
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
    public SpeechTransport.Response post(
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
                    exception.getMessage() == null ? "Transcription request failed" : exception.getMessage(),
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

    private static SpeechTransport.Response toResponse(okhttp3.Response response) throws IOException {
        String requestId = requestId(response);
        Long retryAfterMillis = retryAfterMillis(response);
        ResponseBody responseBody = response.body();
        String body = "";
        if (responseBody != null) {
            // 失败时只截断读取：完整错误体可能很大，而调用方只需要 message/type。
            body = response.isSuccessful()
                    ? responseBody.string()
                    : response.peekBody(MAX_ERROR_BODY_CHARS).string();
        }
        return new SpeechTransport.Response(response.code(), requestId, body, retryAfterMillis);
    }

    @Nullable
    private static String requestId(okhttp3.Response response) {
        String id = response.header("x-request-id");
        if (id == null || id.isBlank()) {
            id = response.header("request-id");
        }
        return id == null || id.isBlank() ? null : id;
    }

    @Nullable
    private static Long retryAfterMillis(okhttp3.Response response) {
        Long millis = parseNonNegativeLong(response.header("retry-after-ms"));
        if (millis != null) {
            return millis;
        }
        Long seconds = parseNonNegativeLong(response.header("Retry-After"));
        return seconds == null ? null : seconds * 1000L;
    }

    @Nullable
    private static Long parseNonNegativeLong(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                // 也可能是 HTTP 日期格式；本实现不解析日期，交给退避算法。
                return null;
            }
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
