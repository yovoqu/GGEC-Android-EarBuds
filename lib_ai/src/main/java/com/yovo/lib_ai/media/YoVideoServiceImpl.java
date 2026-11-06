package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoJob;
import com.yovo.lib_ai.api.YoVideoGeneration;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * 视频生成实现（Qwen Wan3.0）：提交异步任务（{@code X-DashScope-Async: enable}），
 * 再轮询 {@code GET /api/v1/tasks/{task_id}} 直到 {@code output.video_url} 就绪。
 *
 * <p>超时与轮询间隔由 {@link Builder} 控制；超时折成 {@code FAILED} 并给出可读原因，
 * 不抛异常——调用方看到的是一致的 {@link YoJob} 状态机。
 */
public final class YoVideoServiceImpl implements YoVideoGeneration, AutoCloseable {

    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000L;
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = 120_000L;
    private static final long DEFAULT_WRITE_TIMEOUT_MILLIS = 60_000L;
    private static final long DEFAULT_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private static final long DEFAULT_INTERVAL_MILLIS = 2_000L;

    private final MediaTransport transport;
    private final long timeoutMillis;
    private final long intervalMillis;
    private final LongSupplier clock;
    private final YoJobRunner.Sleeper sleeper;
    private final AtomicBoolean closed = new AtomicBoolean();

    /** 供单测注入假传输与假时钟。 */
    YoVideoServiceImpl(
            @NonNull MediaTransport transport,
            long timeoutMillis,
            long intervalMillis,
            @NonNull LongSupplier clock,
            @NonNull YoJobRunner.Sleeper sleeper) {
        this.transport = transport;
        this.timeoutMillis = timeoutMillis;
        this.intervalMillis = intervalMillis;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    @Override
    public YoJob<YoVideoResult> generate(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoVideoRequest request) {
        ensureOpen();
        Objects.requireNonNull(request, "request");
        String key = YoServiceTarget.requireText(apiKey, "apiKey");
        String url = MediaEndpoints.qwenVideo(baseUrl);
        byte[] body = encode(request).toString().getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("Accept", "application/json");
        headers.put("X-DashScope-Async", "enable");
        headers.put("User-Agent", "lib_ai");

        MediaTransport.Response response = transport.post(url, headers, body, "application/json");
        if (!response.isSuccess()) {
            throw YoImageServiceImpl.httpException(response);
        }
        Submit submit = decodeSubmit(response.text());
        if (submit.videoUrl != null) {
            return YoJobRunner.completed(submit.taskId,
                    new YoVideoResult(submit.videoUrl, submit.taskId, submit.requestId,
                            submit.taskStatus, response.text()));
        }
        if (submit.taskId == null || submit.taskId.isBlank()) {
            throw new YoAiProtocolException("Video submit response has no task_id", response.text());
        }
        YoJobRunner.Poller<YoVideoResult> poller = () -> pollOnce(baseUrl, key, submit.taskId);
        return YoJobRunner.poll(submit.taskId, poller, timeoutMillis, intervalMillis, clock, sleeper);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            transport.close();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("YoVideoServiceImpl is closed");
        }
    }

    @NonNull
    private YoJobRunner.Poll<YoVideoResult> pollOnce(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull String taskId) {
        String url = MediaEndpoints.qwenTask(baseUrl, taskId);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Accept", "application/json");
        MediaTransport.Response response = transport.get(url, headers);
        if (!response.isSuccess()) {
            throw YoImageServiceImpl.httpException(response);
        }
        String raw = response.text();
        JSONObject json = objectOf(raw);
        JSONObject output = json.optJSONObject("output");
        String status = output == null ? "" : output.optString("task_status", "");
        if ("SUCCEEDED".equalsIgnoreCase(status)) {
            String videoUrl = optText(output, "video_url");
            if (videoUrl == null) {
                return YoJobRunner.Poll.failed("video task succeeded but output.video_url is missing");
            }
            YoVideoResult result = new YoVideoResult(
                    videoUrl, taskId, optText(json, "request_id"), status, raw);
            return YoJobRunner.Poll.succeeded(result);
        }
        if ("FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status) || "UNKNOWN".equalsIgnoreCase(status)) {
            String message = output == null ? null : optText(output, "message");
            if (message == null) {
                message = optText(json, "message");
            }
            if (message == null) {
                message = status;
            }
            return YoJobRunner.Poll.failed(message);
        }
        return YoJobRunner.Poll.running();
    }

    // ---------------------------------------------------------------- 编解码（供测试）

    @NonNull
    static JSONObject encode(@NonNull YoVideoRequest request) {
        try {
            JSONObject input = new JSONObject();
            input.put("prompt", request.prompt());
            if (!request.media().isEmpty()) {
                JSONArray media = new JSONArray();
                for (YoVideoRequest.MediaRef ref : request.media()) {
                    media.put(new JSONObject().put("type", ref.type()).put("url", ref.url()));
                }
                input.put("media", media);
            }
            JSONObject parameters = new JSONObject();
            if (request.resolution() != null) {
                parameters.put("resolution", request.resolution());
            }
            if (request.ratio() != null) {
                parameters.put("ratio", request.ratio());
            }
            if (request.duration() != null) {
                parameters.put("duration", request.duration());
            }
            if (request.promptExtend() != null) {
                parameters.put("prompt_extend", request.promptExtend());
            }
            if (request.negativePrompt() != null) {
                parameters.put("negative_prompt", request.negativePrompt());
            }
            if (request.seed() != null) {
                parameters.put("seed", request.seed());
            }
            return new JSONObject()
                    .put("model", request.model())
                    .put("input", input)
                    .put("parameters", parameters);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Unable to encode video request", exception);
        }
    }

    @NonNull
    static Submit decodeSubmit(@NonNull String raw) {
        JSONObject json = objectOf(raw);
        JSONObject output = json.optJSONObject("output");
        if (output == null) {
            String code = optText(json, "code");
            throw new YoAiProtocolException(
                    code == null ? "Video submit response has no output" : "Video submit error: " + code, raw);
        }
        return new Submit(
                optText(output, "task_id"),
                optText(output, "video_url"),
                optText(output, "task_status"),
                optText(json, "request_id"));
    }

    /** 提交响应的关键字段。 */
    static final class Submit {
        final String taskId;
        final String videoUrl;
        final String taskStatus;
        final String requestId;

        Submit(String taskId, String videoUrl, String taskStatus, String requestId) {
            this.taskId = taskId;
            this.videoUrl = videoUrl;
            this.taskStatus = taskStatus;
            this.requestId = requestId;
        }
    }

    @NonNull
    private static JSONObject objectOf(@NonNull String raw) {
        if (raw == null || raw.isBlank()) {
            throw new YoAiProtocolException("Response body is empty", raw);
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Response body is not a JSON object", raw, exception);
        }
    }

    @Nullable
    private static String optText(@NonNull JSONObject json, @NonNull String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        return value == null || value == JSONObject.NULL ? null : String.valueOf(value);
    }

    public static final class Builder {

        private long connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
        private long readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
        private long writeTimeoutMillis = DEFAULT_WRITE_TIMEOUT_MILLIS;
        private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        private long intervalMillis = DEFAULT_INTERVAL_MILLIS;

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

        /** 轮询总预算（毫秒）。 */
        @NonNull
        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = positive(timeoutMillis, "timeoutMillis");
            return this;
        }

        /** 两次轮询之间的间隔（毫秒）。 */
        @NonNull
        public Builder intervalMillis(long intervalMillis) {
            this.intervalMillis = positive(intervalMillis, "intervalMillis");
            return this;
        }

        @NonNull
        public YoVideoServiceImpl build() {
            return new YoVideoServiceImpl(
                    new OkHttpMediaTransport(connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis),
                    timeoutMillis,
                    intervalMillis,
                    YoJobRunner.systemClock(),
                    YoJobRunner.threadSleeper());
        }

        private static long positive(long value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be > 0");
            }
            return value;
        }
    }
}
