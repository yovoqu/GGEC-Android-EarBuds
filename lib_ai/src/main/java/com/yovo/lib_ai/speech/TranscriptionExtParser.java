package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 转写结果的扩展解码：{@code verbose_json} 的 segments/words、{@code diarized_json} 的 speaker、
 * 以及 {@code stream=true} 的 SSE（{@code transcript.text.delta/done}）。
 *
 * <p>单独成类的原因：这些都是纯报文变换，可以脱离网络用 fixture 直接断言；
 * 放在 {@link YoWhisperSpeech} 里会让那个类同时承担"发请求"和"五种响应格式"两件事。
 */
final class TranscriptionExtParser {

    private TranscriptionExtParser() {
    }

    /**
     * 解析一份非流式响应 JSON。{@code text} 必须存在（静音是 {@code ""}，不是缺失）。
     */
    @NonNull
    static YoTranscription decode(@NonNull JSONObject json, @NonNull String raw) {
        String text = json.optString("text", "");
        String language = optText(json, "language");
        Double duration = optDouble(json, "duration");
        List<YoSegment> segments = parseSegments(json.optJSONArray("segments"));
        List<YoWord> words = parseWords(json.optJSONArray("words"), null);
        List<String> speakers = collectSpeakers(segments, words);
        YoTranscriptionUsage usage = parseUsage(json.optJSONObject("usage"));
        return new YoTranscription(text, language, duration, raw, segments, words, speakers, usage);
    }

    /**
     * 解析流式 SSE。逐条 {@code transcript.text.delta} 拼出文本，{@code transcript.text.done}
     * 给出最终文本与用量；两者不一致时以 done 为准（服务端可能做后处理）。
     */
    @NonNull
    static YoTranscription parseStream(@NonNull String sse) {
        StringBuilder deltas = new StringBuilder();
        String doneText = null;
        String language = null;
        Double duration = null;
        YoTranscriptionUsage usage = null;
        final String[] lines = sse.split("\n", -1);
        StringBuilder data = new StringBuilder();
        for (String rawLine : lines) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            if (line.isEmpty()) {
                SsePayload payload = payloadOf(data);
                data.setLength(0);
                if (payload != null) {
                    String type = payload.type;
                    if ("transcript.text.delta".equals(type)) {
                        deltas.append(payload.json.optString("delta", ""));
                    } else if ("transcript.text.done".equals(type)) {
                        doneText = payload.json.optString("text", deltas.toString());
                        language = optText(payload.json, "language");
                        duration = optDouble(payload.json, "duration");
                        usage = parseUsage(payload.json.optJSONObject("usage"));
                    }
                }
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
        SsePayload last = payloadOf(data);
        if (last != null) {
            if ("transcript.text.delta".equals(last.type)) {
                deltas.append(last.json.optString("delta", ""));
            } else if ("transcript.text.done".equals(last.type)) {
                doneText = last.json.optString("text", deltas.toString());
                language = optText(last.json, "language");
                duration = optDouble(last.json, "duration");
                usage = parseUsage(last.json.optJSONObject("usage"));
            }
        }
        String text = doneText != null ? doneText : deltas.toString();
        return new YoTranscription(text, language, duration, sse, List.of(), List.of(), List.of(), usage);
    }

    // ---------------------------------------------------------------- 内部解析

    @NonNull
    private static List<YoSegment> parseSegments(@Nullable JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<YoSegment> segments = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            JSONObject segment = array.optJSONObject(index);
            if (segment == null) {
                continue;
            }
            String id = optText(segment, "id");
            double start = optDoubleOrZero(segment, "start");
            double end = optDoubleOrZero(segment, "end");
            String text = segment.optString("text", "");
            String speaker = optText(segment, "speaker");
            List<YoWord> words = parseWords(segment.optJSONArray("words"), speaker);
            segments.add(new YoSegment(id, start, end, text, speaker, words));
        }
        return segments;
    }

    @NonNull
    private static List<YoWord> parseWords(@Nullable JSONArray array, @Nullable String defaultSpeaker) {
        if (array == null) {
            return List.of();
        }
        List<YoWord> words = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            JSONObject word = array.optJSONObject(index);
            if (word == null) {
                continue;
            }
            String text = word.optString("word", word.optString("text", ""));
            String speaker = optText(word, "speaker");
            words.add(new YoWord(
                    text,
                    optDoubleOrZero(word, "start"),
                    optDoubleOrZero(word, "end"),
                    speaker == null ? defaultSpeaker : speaker));
        }
        return words;
    }

    @NonNull
    private static List<String> collectSpeakers(
            @NonNull List<YoSegment> segments,
            @NonNull List<YoWord> words) {
        Set<String> speakers = new LinkedHashSet<>();
        for (YoSegment segment : segments) {
            if (segment.speaker() != null) {
                speakers.add(segment.speaker());
            }
            for (YoWord word : segment.words()) {
                if (word.speaker() != null) {
                    speakers.add(word.speaker());
                }
            }
        }
        for (YoWord word : words) {
            if (word.speaker() != null) {
                speakers.add(word.speaker());
            }
        }
        return List.copyOf(speakers);
    }

    @Nullable
    private static YoTranscriptionUsage parseUsage(@Nullable JSONObject usage) {
        if (usage == null) {
            return null;
        }
        Integer input = optInt(usage, "input_tokens");
        Integer output = optInt(usage, "output_tokens");
        Integer total = optInt(usage, "total_tokens");
        Double seconds = optDouble(usage, "seconds");
        if (seconds == null) {
            seconds = optDouble(usage, "duration");
        }
        if (input == null && output == null && total == null && seconds == null) {
            return null;
        }
        return new YoTranscriptionUsage(input, output, total, seconds);
    }

    private static final class SsePayload {
        private final String type;
        private final JSONObject json;

        private SsePayload(String type, JSONObject json) {
            this.type = type;
            this.json = json;
        }
    }

    @Nullable
    private static SsePayload payloadOf(@NonNull StringBuilder data) {
        if (data.length() == 0) {
            return null;
        }
        String payload = data.toString().trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(payload);
            String type = json.optString("type", "");
            return new SsePayload(type, json);
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Transcription SSE data is not a JSON object", payload, exception);
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

    private static double optDoubleOrZero(@NonNull JSONObject json, @NonNull String key) {
        Double value = optDouble(json, key);
        return value == null ? 0D : value;
    }

    @Nullable
    private static Integer optInt(@NonNull JSONObject json, @NonNull String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
