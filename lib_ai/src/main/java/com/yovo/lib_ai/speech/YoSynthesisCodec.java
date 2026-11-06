package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 合成请求编码与响应解码。两种方言的字段名差异只在这里出现一次。
 */
final class YoSynthesisCodec {

    private YoSynthesisCodec() {
    }

    // ---------------------------------------------------------------- 请求编码

    /**
     * 语义请求 → wire JSON。
     *
     * @throws IllegalArgumentException 方言必填字段缺失（voice）
     */
    @NonNull
    static JSONObject encode(@NonNull YoSynthesisRequest request) {
        try {
            return switch (request.dialect()) {
                case OPENAI -> encodeOpenAi(request);
                case QWEN -> encodeQwen(request);
            };
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Unable to encode synthesis request", exception);
        }
    }

    @NonNull
    private static JSONObject encodeOpenAi(@NonNull YoSynthesisRequest request) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", request.model());
        json.put("input", request.input());
        json.put("voice", requireText(request.voice(), "voice"));
        putIfPresent(json, "instructions", request.instructions());
        putIfPresent(json, "response_format", request.responseFormat());
        if (request.speed() != null) {
            json.put("speed", request.speed());
        }
        putIfPresent(json, "stream_format", request.streamFormat());
        return json;
    }

    @NonNull
    private static JSONObject encodeQwen(@NonNull YoSynthesisRequest request) throws JSONException {
        JSONObject input = new JSONObject();
        input.put("text", request.input());
        input.put("voice", requireText(request.voice(), "voice"));
        input.put("format", request.format() == null ? "wav" : request.format());
        input.put("sample_rate", request.sampleRate() == null ? 24000 : request.sampleRate());
        JSONObject json = new JSONObject();
        json.put("model", request.model());
        json.put("input", input);
        return json;
    }

    // ---------------------------------------------------------------- 响应解码

    /** OpenAI 成功响应是二进制音频，内容类型以 {@code audio/} 开头。 */
    @NonNull
    static YoSynthesisResult decodeOpenAiBinary(
            @NonNull byte[] body,
            @Nullable String contentType,
            @Nullable String requestId,
            @Nullable String requestedFormat) {
        if (body.length == 0) {
            throw new YoAiProtocolException("Speech response body is empty", null);
        }
        String mime = mimeTypeOf(contentType, requestedFormat);
        return new YoSynthesisResult(body, mime, null, requestId, null);
    }

    /**
     * Qwen 非流式响应：{@code output.audio.url} 或 {@code output.audio.data}（Base64）。
     */
    @NonNull
    static YoSynthesisResult decodeQwen(@NonNull String raw, @Nullable String requestId) {
        JSONObject json = objectOf(raw);
        JSONObject output = json.optJSONObject("output");
        JSONObject audio = output == null ? null : output.optJSONObject("audio");
        String url = audio == null ? null : optText(audio, "url");
        String data = audio == null ? null : optText(audio, "data");
        String rid = requestId == null ? optText(json, "request_id") : requestId;
        if (data != null && !data.isBlank()) {
            byte[] bytes;
            try {
                bytes = Base64s.decode(data);
            } catch (IllegalArgumentException exception) {
                throw new YoAiProtocolException("Qwen audio.data is not valid base64", raw, exception);
            }
            return new YoSynthesisResult(bytes, "audio/wav", url, rid, raw);
        }
        if (url != null && !url.isBlank()) {
            return new YoSynthesisResult(null, null, url, rid, raw);
        }
        throw new YoAiProtocolException("Qwen synthesis response has neither audio.url nor audio.data", raw);
    }

    /**
     * OpenAI {@code stream_format=sse}：逐条 {@code speech.audio.delta}（Base64）拼接成完整音频。
     */
    @NonNull
    static YoSynthesisResult decodeOpenAiSse(@NonNull String sse, @Nullable String requestId) {
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        StringBuilder data = new StringBuilder();
        String[] lines = sse.split("\n", -1);
        boolean any = false;
        for (String rawLine : lines) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            if (line.isEmpty()) {
                any |= flushSseData(data, audio);
                data.setLength(0);
                continue;
            }
            if (line.startsWith("data:")) {
                String value = line.substring(5);
                if (!value.isEmpty() && value.charAt(0) == ' ') {
                    value = value.substring(1);
                }
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(value);
            }
        }
        any |= flushSseData(data, audio);
        if (!any) {
            throw new YoAiProtocolException("Speech SSE contains no audio delta", sse);
        }
        return new YoSynthesisResult(audio.toByteArray(), "audio/wav", null, requestId, null);
    }

    private static boolean flushSseData(StringBuilder data, ByteArrayOutputStream audio) {
        if (data.length() == 0) {
            return false;
        }
        String payload = data.toString().trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return false;
        }
        JSONObject event = objectOf(payload);
        String base64 = event.optString("audio", "");
        if (base64.isEmpty()) {
            base64 = event.optString("delta", "");
        }
        if (base64.isEmpty()) {
            return false;
        }
        byte[] bytes = Base64s.decode(base64);
        audio.writeBytes(bytes);
        return true;
    }

    // ---------------------------------------------------------------- 辅助

    @NonNull
    static String mimeTypeOf(@Nullable String contentType, @Nullable String format) {
        if (contentType != null && !contentType.isBlank()) {
            String base = contentType;
            int semi = base.indexOf(';');
            if (semi >= 0) {
                base = base.substring(0, semi);
            }
            base = base.trim();
            if (!base.isEmpty() && !"application/json".equalsIgnoreCase(base)) {
                return base;
            }
        }
        String normalized = format == null ? "" : format.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "opus" -> "audio/ogg";
            case "aac" -> "audio/aac";
            case "flac" -> "audio/flac";
            case "pcm" -> "audio/pcm";
            default -> "application/octet-stream";
        };
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

    private static void putIfPresent(@NonNull JSONObject json, @NonNull String key, @Nullable String value)
            throws JSONException {
        if (value != null) {
            json.put(key, value);
        }
    }

    @NonNull
    private static String requireText(@Nullable String value, @NonNull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required for this dialect");
        }
        return value.trim();
    }

    @NonNull
    static byte[] utf8(@NonNull JSONObject json) {
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
}
