package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoImageGeneration;
import com.yovo.lib_ai.api.YoJob;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 图像生成实现：Qwen 多模态同步与 OpenAI Images。
 *
 * <p>两种方言都是同步返回，因此 {@code generate} 返回的 {@link YoJob} 直接是
 * {@link com.yovo.lib_ai.api.YoJobStatus#SUCCEEDED}，只是为了和视频任务统一接口。
 */
public final class YoImageServiceImpl implements YoImageGeneration, AutoCloseable {

    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000L;
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = 120_000L;
    private static final long DEFAULT_WRITE_TIMEOUT_MILLIS = 60_000L;

    private final MediaTransport transport;
    private final AtomicBoolean closed = new AtomicBoolean();

    YoImageServiceImpl(@NonNull MediaTransport transport) {
        this.transport = transport;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    @Override
    public YoJob<YoImageResult> generate(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull YoImageRequest request) {
        ensureOpen();
        Objects.requireNonNull(request, "request");
        String key = YoServiceTarget.requireText(apiKey, "apiKey");
        String url = request.dialect() == YoImageRequest.Dialect.OPENAI
                ? MediaEndpoints.openAiImages(baseUrl)
                : MediaEndpoints.qwenImage(baseUrl);
        byte[] body = encode(request).toString().getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "lib_ai");

        MediaTransport.Response response = transport.post(url, headers, body, "application/json");
        if (!response.isSuccess()) {
            throw httpException(response);
        }
        YoImageResult result = decode(request.dialect(), response.text(), response.requestId());
        return YoJobRunner.completed(result.taskId(), result);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            transport.close();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("YoImageServiceImpl is closed");
        }
    }

    // ---------------------------------------------------------------- 编解码（包内可见，供测试）

    @NonNull
    static JSONObject encode(@NonNull YoImageRequest request) {
        try {
            return request.dialect() == YoImageRequest.Dialect.OPENAI
                    ? encodeOpenAi(request)
                    : encodeQwen(request);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Unable to encode image request", exception);
        }
    }

    @NonNull
    private static JSONObject encodeQwen(@NonNull YoImageRequest request) throws JSONException {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("text", request.prompt()));
        if (request.referenceImage() != null) {
            content.put(new JSONObject().put("image", request.referenceImage()));
        }
        JSONObject message = new JSONObject().put("role", "user").put("content", content);
        JSONObject input = new JSONObject().put("messages", new JSONArray().put(message));
        JSONObject parameters = new JSONObject();
        if (request.size() != null) {
            parameters.put("size", request.size());
        }
        parameters.put("n", request.n());
        if (request.promptExtend() != null) {
            parameters.put("prompt_extend", request.promptExtend());
        }
        if (request.seed() != null) {
            parameters.put("seed", request.seed());
        }
        if (request.watermark() != null) {
            parameters.put("watermark", request.watermark());
        }
        if (request.negativePrompt() != null) {
            parameters.put("negative_prompt", request.negativePrompt());
        }
        return new JSONObject()
                .put("model", request.model())
                .put("input", input)
                .put("parameters", parameters);
    }

    @NonNull
    private static JSONObject encodeOpenAi(@NonNull YoImageRequest request) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", request.model());
        json.put("prompt", request.prompt());
        json.put("n", request.n());
        if (request.size() != null) {
            json.put("size", request.size());
        }
        if (request.responseFormat() != null) {
            json.put("response_format", request.responseFormat());
        }
        return json;
    }

    @NonNull
    static YoImageResult decode(
            @NonNull YoImageRequest.Dialect dialect,
            @NonNull String raw,
            @Nullable String requestId) {
        JSONObject json = objectOf(raw);
        String rid = requestId == null ? optText(json, "request_id") : requestId;
        List<String> urls = new ArrayList<>();
        List<String> base64 = new ArrayList<>();
        List<String> revised = new ArrayList<>();
        String taskId = null;
        if (dialect == YoImageRequest.Dialect.OPENAI) {
            JSONArray data = json.optJSONArray("data");
            if (data != null) {
                for (int index = 0; index < data.length(); index++) {
                    JSONObject item = data.optJSONObject(index);
                    if (item == null) {
                        continue;
                    }
                    String url = optText(item, "url");
                    if (url != null) {
                        urls.add(url);
                    }
                    String b64 = optText(item, "b64_json");
                    if (b64 != null) {
                        base64.add(b64);
                    }
                    String revisedPrompt = optText(item, "revised_prompt");
                    if (revisedPrompt != null) {
                        revised.add(revisedPrompt);
                    }
                }
            }
        } else {
            String code = optText(json, "code");
            JSONObject output = json.optJSONObject("output");
            if (output == null) {
                throw new YoAiProtocolException(
                        code == null ? "Qwen image response has no output" : "Qwen image error: " + code, raw);
            }
            taskId = optText(output, "task_id");
            JSONArray choices = output.optJSONArray("choices");
            if (choices != null) {
                for (int c = 0; c < choices.length(); c++) {
                    JSONObject choice = choices.optJSONObject(c);
                    JSONObject message = choice == null ? null : choice.optJSONObject("message");
                    JSONArray content = message == null ? null : message.optJSONArray("content");
                    if (content == null) {
                        continue;
                    }
                    for (int i = 0; i < content.length(); i++) {
                        JSONObject part = content.optJSONObject(i);
                        if (part == null) {
                            continue;
                        }
                        String url = optText(part, "image");
                        if (url != null) {
                            urls.add(url);
                        }
                    }
                }
            }
        }
        if (urls.isEmpty() && base64.isEmpty()) {
            throw new YoAiProtocolException("Image response has no url or b64_json", raw);
        }
        return new YoImageResult(urls, base64, revised, taskId, rid, raw);
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

    @NonNull
    static YoAiHttpException httpException(@NonNull MediaTransport.Response response) {
        int status = response.statusCode();
        String body = response.text();
        String message = "HTTP " + status;
        String type = null;
        if (body.trim().startsWith("{")) {
            try {
                JSONObject json = new JSONObject(body);
                JSONObject error = json.optJSONObject("error");
                if (error != null) {
                    String m = error.optString("message", "");
                    if (!m.isBlank()) {
                        message = m;
                    }
                    type = error.optString("type", null);
                } else {
                    String m = json.optString("message", "");
                    if (!m.isBlank()) {
                        message = m;
                    }
                    type = json.optString("code", null);
                }
            } catch (JSONException ignored) {
                // 保留 HTTP 状态作为消息。
            }
        }
        return new YoAiHttpException(message, status, response.requestId(), type, body);
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
        public YoImageServiceImpl build() {
            return new YoImageServiceImpl(
                    new OkHttpMediaTransport(connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis));
        }

        private static long positive(long value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be > 0");
            }
            return value;
        }
    }
}
