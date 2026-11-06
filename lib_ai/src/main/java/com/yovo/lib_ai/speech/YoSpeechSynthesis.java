package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoSynthesis;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTS 合成客户端：OpenAI {@code /v1/audio/speech} 与 Qwen
 * {@code /api/v1/services/audio/tts/SpeechSynthesizer} 两种方言。
 *
 * <p>错误策略：HTTP 非 2xx 折成 {@link YoAiHttpException}，响应体不合协议折成
 * {@link YoAiProtocolException}，两者都能被 {@code YoAiErrors.kindOf} 归类。
 *
 * <p>生命周期：一个实例复用一个连接池，适合整个通话期持有；用完 {@link #close()}。
 */
public final class YoSpeechSynthesis implements YoSynthesis, AutoCloseable {

    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000L;
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = 120_000L;
    private static final long DEFAULT_WRITE_TIMEOUT_MILLIS = 60_000L;

    private final SynthesisTransport transport;
    private final AtomicBoolean closed = new AtomicBoolean();

    /** 供单测注入假传输。 */
    YoSpeechSynthesis(@NonNull SynthesisTransport transport) {
        this.transport = transport;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    @Override
    public YoSynthesisResult synthesize(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoSynthesisRequest request) {
        ensureOpen();
        Objects.requireNonNull(request, "request");
        String key = YoServiceTarget.requireText(apiKey, "apiKey");
        String url = urlFor(baseUrl, request.dialect());
        byte[] body = YoSynthesisCodec.utf8(YoSynthesisCodec.encode(request));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("Accept", request.dialect() == YoSynthesisRequest.Dialect.OPENAI
                ? "audio/*, text/event-stream" : "application/json");
        headers.put("User-Agent", "lib_ai");

        SynthesisTransport.Response response = transport.post(url, headers, body, "application/json");
        if (!response.isSuccess()) {
            throw httpException(response);
        }
        return decodeSuccess(response, request);
    }

    @NonNull
    @Override
    public YoAiCall stream(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoSynthesisRequest request,
            @NonNull YoSynthesisStreamListener listener) {
        ensureOpen();
        Objects.requireNonNull(listener, "listener");
        Cancellation cancellation = new Cancellation();
        try {
            boolean sse = request.dialect() == YoSynthesisRequest.Dialect.OPENAI
                    && "sse".equalsIgnoreCase(request.streamFormat());
            if (sse) {
                YoSynthesisResult result = synthesize(baseUrl, apiKey, request);
                byte[] audio = result.audioBytes();
                if (audio != null && !cancellation.isCancelled()) {
                    listener.onAudio(audio);
                }
                if (!cancellation.isCancelled()) {
                    listener.onComplete(result);
                }
            } else {
                YoSynthesisResult result = synthesize(baseUrl, apiKey, request);
                byte[] audio = result.audioBytes();
                if (audio != null && !cancellation.isCancelled()) {
                    listener.onAudio(audio);
                }
                if (!cancellation.isCancelled()) {
                    listener.onComplete(result);
                }
            }
        } catch (YoAiException failure) {
            if (!cancellation.isCancelled()) {
                listener.onFailure(failure);
            }
        }
        return cancellation;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            transport.close();
        }
    }

    @NonNull
    private YoSynthesisResult decodeSuccess(
            @NonNull SynthesisTransport.Response response,
            @NonNull YoSynthesisRequest request) {
        String contentType = response.contentType();
        if (request.dialect() == YoSynthesisRequest.Dialect.QWEN) {
            return YoSynthesisCodec.decodeQwen(response.text(), response.requestId());
        }
        boolean sse = contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("text/event-stream");
        if (sse) {
            return YoSynthesisCodec.decodeOpenAiSse(response.text(), response.requestId());
        }
        return YoSynthesisCodec.decodeOpenAiBinary(
                response.bodyBytes(), contentType, response.requestId(), request.responseFormat());
    }

    @NonNull
    private static String urlFor(@NonNull String baseUrl, @NonNull YoSynthesisRequest.Dialect dialect) {
        return switch (dialect) {
            case OPENAI -> SynthesisEndpoints.openAiSpeech(baseUrl);
            case QWEN -> SynthesisEndpoints.qwenSpeech(baseUrl);
        };
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("YoSpeechSynthesis is closed");
        }
    }

    @NonNull
    private static YoAiHttpException httpException(@NonNull SynthesisTransport.Response response) {
        int status = response.statusCode();
        String body = response.text();
        String message = "HTTP " + status;
        String type = null;
        if (body.trim().startsWith("{")) {
            try {
                JSONObject json = new JSONObject(body);
                JSONObject error = json.optJSONObject("error");
                if (error != null) {
                    String errorMessage = error.optString("message", "");
                    if (!errorMessage.isBlank()) {
                        message = errorMessage;
                    }
                    type = error.optString("type", null);
                } else {
                    String top = json.optString("message", "");
                    if (!top.isBlank()) {
                        message = top;
                    }
                    type = json.optString("code", null);
                }
            } catch (JSONException ignored) {
                // 错误体不是 JSON，保留 "HTTP <status>"。
            }
        }
        return new YoAiHttpException(message, status, response.requestId(), type, body);
    }

    /** 极简取消句柄：流式回调在片段之间检查该标志。 */
    private static final class Cancellation implements YoAiCall {

        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        boolean isCancelled() {
            return cancelled.get();
        }
    }

    public static final class Builder {

        private long connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
        private long readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
        private long writeTimeoutMillis = DEFAULT_WRITE_TIMEOUT_MILLIS;

        private Builder() {
        }

        @NonNull
        public Builder connectTimeoutMillis(long connectTimeoutMillis) {
            this.connectTimeoutMillis = positive(connectTimeoutMillis, "connectTimeoutMillis");
            return this;
        }

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
        public YoSpeechSynthesis build() {
            return new YoSpeechSynthesis(
                    new OkHttpSynthesisTransport(connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis));
        }

        private static long positive(long value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be > 0");
            }
            return value;
        }
    }
}
