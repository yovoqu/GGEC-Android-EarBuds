package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.error.YoAiTransportException;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whisper 兼容转写客户端：{@code POST {baseUrl}/audio/transcriptions}，multipart 上传 wav。
 *
 * <p>职责边界：只做一次转写请求并解出文本。不做录音、不做重采样、不做合成，也不持有通话状态。
 *
 * <p>错误策略（不吞异常）：HTTP 非 2xx 折成 {@link YoAiHttpException}，
 * 网络/超时折成 {@link YoAiTransportException}，响应体不合协议折成
 * {@link YoAiProtocolException}。三者都能被 {@link YoAiErrors#kindOf} 归类成
 * {@link YoAiErrorKind}，界面只需要看分类，不需要解析状态码。
 *
 * <p>重试：只重试"再发一次可能成功"的失败——408/429/5xx 与网络中断。
 * 鉴权失败、参数错误、协议不兼容一律立即抛出；读超时也不重试，
 * 因为一次读超时已经吃掉了整个读预算，再等一轮只会让通话页的"思考"阶段翻倍。
 *
 * <p>生命周期：一个实例复用一个连接池，适合整个通话期持有；用完 {@link #close()}。
 * 关闭后正在进行的请求会被取消，后续调用抛 {@link IllegalStateException}。
 */
public final class YoWhisperSpeech implements YoSpeech, AutoCloseable {

    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000L;
    /** 长录音的上传与识别都可能久，读超时给足；它不是"整次请求"的总时限。 */
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = 120_000L;
    private static final long DEFAULT_WRITE_TIMEOUT_MILLIS = 60_000L;
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final long DEFAULT_INITIAL_RETRY_DELAY_MILLIS = 200L;
    private static final long DEFAULT_MAX_RETRY_DELAY_MILLIS = 5_000L;
    private static final int MAX_ERROR_BODY_CHARS = 8192;

    private final SpeechTransport transport;
    private final int maxRetries;
    private final long initialRetryDelayMillis;
    private final long maxRetryDelayMillis;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 供单测注入假传输。包内可见：不把内部缝暴露成公开 API，
     * 换掉传输层不是使用方的需求，而是我们这里要能断言的路径。
     */
    YoWhisperSpeech(
            @NonNull SpeechTransport transport,
            int maxRetries,
            long initialRetryDelayMillis,
            long maxRetryDelayMillis) {
        this.transport = transport;
        this.maxRetries = Math.max(0, maxRetries);
        this.initialRetryDelayMillis = Math.max(0L, initialRetryDelayMillis);
        this.maxRetryDelayMillis = Math.max(0L, maxRetryDelayMillis);
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    @Override
    public YoTranscription transcribe(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoTranscriptionRequest request) {
        ensureOpen();
        Objects.requireNonNull(request, "request");
        String key = YoServiceTarget.requireText(apiKey, "apiKey");
        String url = SpeechEndpoints.transcriptionUrl(baseUrl);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "lib_ai");

        String boundary = SpeechMultipart.boundary();
        byte[] body = SpeechMultipart.encode(request, boundary);
        String contentType = SpeechMultipart.contentType(boundary);

        int attempt = 0;
        while (true) {
            SpeechTransport.Response response;
            try {
                response = transport.post(url, headers, body, contentType);
            } catch (YoAiException failure) {
                if (attempt < maxRetries && isRetryableTransportFailure(failure)) {
                    attempt++;
                    sleep(backoff(attempt - 1));
                    continue;
                }
                throw failure;
            }
            if (isSuccess(response.statusCode())) {
                return decode(response);
            }
            if (attempt < maxRetries && isRetryableStatus(response.statusCode())) {
                attempt++;
                sleep(retryDelay(response, attempt - 1));
                continue;
            }
            throw httpException(response);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            transport.close();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("YoWhisperSpeech is closed");
        }
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    /** 与 {@code OkHttpTransport.shouldRetry} 保持同一套状态码判断。 */
    private static boolean isRetryableStatus(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private static boolean isRetryableTransportFailure(YoAiException failure) {
        return YoAiErrors.kindOf(failure) == YoAiErrorKind.NETWORK;
    }

    private long retryDelay(SpeechTransport.Response response, int attempt) {
        Long retryAfterMillis = response.retryAfterMillis();
        if (retryAfterMillis == null) {
            return backoff(attempt);
        }
        return Math.min(Math.max(retryAfterMillis, 0L), maxRetryDelayMillis);
    }

    private long backoff(int attempt) {
        long factor = 1L << Math.min(attempt, 10);
        long delay = initialRetryDelayMillis * factor;
        if (delay < 0) {
            return maxRetryDelayMillis;
        }
        return Math.min(delay, maxRetryDelayMillis);
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw YoAiTransportException.cancelled(exception);
        }
    }

    @NonNull
    private static YoTranscription decode(SpeechTransport.Response response) {
        String raw = response.body() == null ? "" : response.body();
        if (raw.isBlank()) {
            throw new YoAiProtocolException("Transcription response body is empty", raw);
        }
        JSONObject json;
        try {
            json = new JSONObject(raw);
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Transcription response is not a JSON object", raw, exception);
        }
        if (!json.has("text") || json.isNull("text")) {
            // 静音会回 ""，但完全没有 text 字段说明地址指到了别的接口，不是"没听到"。
            throw new YoAiProtocolException("Transcription response has no text field", raw);
        }
        return new YoTranscription(
                json.optString("text", ""),
                optText(json, "language"),
                optDouble(json, "duration"),
                raw);
    }

    @NonNull
    private static YoAiHttpException httpException(SpeechTransport.Response response) {
        int status = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        String message = "HTTP " + status;
        String type = null;
        if (body.trim().startsWith("{")) {
            try {
                JSONObject json = new JSONObject(body);
                JSONObject error = json.optJSONObject("error");
                if (error != null) {
                    String errorMessage = optText(error, "message");
                    if (errorMessage != null && !errorMessage.isBlank()) {
                        message = errorMessage;
                    }
                    type = optText(error, "type");
                } else {
                    String topMessage = optText(json, "message");
                    if (topMessage != null && !topMessage.isBlank()) {
                        message = topMessage;
                    }
                }
            } catch (JSONException ignored) {
                // 错误体不是 JSON（网关的 HTML 错误页等），保留 "HTTP <status>" 作为消息。
            }
        }
        return new YoAiHttpException(
                message,
                status,
                response.requestId(),
                type,
                body.length() <= MAX_ERROR_BODY_CHARS ? body : body.substring(0, MAX_ERROR_BODY_CHARS));
    }

    @Nullable
    private static String optText(@NonNull JSONObject json, @NonNull String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        return value == null || value == JSONObject.NULL ? null : String.valueOf(value);
    }

    /** 少数网关把 {@code duration} 当字符串给；两种都接受，都没有就留 null。 */
    @Nullable
    private static Double optDouble(@NonNull JSONObject json, @NonNull String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static final class Builder {

        private long connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
        private long readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
        private long writeTimeoutMillis = DEFAULT_WRITE_TIMEOUT_MILLIS;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private long initialRetryDelayMillis = DEFAULT_INITIAL_RETRY_DELAY_MILLIS;
        private long maxRetryDelayMillis = DEFAULT_MAX_RETRY_DELAY_MILLIS;

        private Builder() {
        }

        @NonNull
        public Builder connectTimeoutMillis(long connectTimeoutMillis) {
            this.connectTimeoutMillis = positive(connectTimeoutMillis, "connectTimeoutMillis");
            return this;
        }

        /** 两次读到数据之间允许的最长间隔，不是整次请求的总时限。 */
        @NonNull
        public Builder readTimeoutMillis(long readTimeoutMillis) {
            this.readTimeoutMillis = positive(readTimeoutMillis, "readTimeoutMillis");
            return this;
        }

        @NonNull
        public Builder writeTimeoutMillis(long writeTimeoutMillis) {
            this.writeTimeoutMillis = positive(writeTimeoutMillis, "writeTimeoutMillis");
            return this;
        }

        @NonNull
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        @NonNull
        public Builder initialRetryDelayMillis(long initialRetryDelayMillis) {
            if (initialRetryDelayMillis < 0) {
                throw new IllegalArgumentException("initialRetryDelayMillis must be >= 0");
            }
            this.initialRetryDelayMillis = initialRetryDelayMillis;
            return this;
        }

        @NonNull
        public Builder maxRetryDelayMillis(long maxRetryDelayMillis) {
            if (maxRetryDelayMillis < 0) {
                throw new IllegalArgumentException("maxRetryDelayMillis must be >= 0");
            }
            this.maxRetryDelayMillis = maxRetryDelayMillis;
            return this;
        }

        @NonNull
        public YoWhisperSpeech build() {
            return new YoWhisperSpeech(
                    new OkHttpSpeechTransport(connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis),
                    maxRetries,
                    initialRetryDelayMillis,
                    maxRetryDelayMillis);
        }

        private static long positive(long value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be > 0");
            }
            return value;
        }
    }
}
