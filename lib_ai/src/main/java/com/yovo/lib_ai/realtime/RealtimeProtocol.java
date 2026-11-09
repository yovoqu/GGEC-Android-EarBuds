package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.protocol.UsageFields;
import com.yovo.lib_ai.models.YoServiceEndpoints;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Realtime 兼容协议编解码器。直接把网络帧解析为公开输出或内部控制流帧，消除多余事件翻译层。
 */
final class RealtimeProtocol {

    /** 解析结果：直接区分建连、配置确认或对外交付的强类型输出。 */
    sealed interface Frame {
        record SessionCreated() implements Frame {
        }

        record SessionUpdated() implements Frame {
        }

        record Output(@NonNull YoRealtimeOutput output) implements Frame {
        }
    }

    static final String TYPE_SESSION_UPDATE = "session.update";
    static final String TYPE_SESSION_CREATED = "session.created";
    static final String TYPE_AUDIO_APPEND = "input_audio_buffer.append";
    static final String TYPE_RESPONSE_CANCEL = "response.cancel";
    static final String TYPE_SPEECH_STARTED = "input_audio_buffer.speech_started";
    static final String TYPE_SPEECH_STOPPED = "input_audio_buffer.speech_stopped";
    static final String TYPE_AUDIO_DELTA = "response.audio.delta";
    static final String TYPE_OUTPUT_AUDIO_DELTA = "response.output_audio.delta";
    static final String TYPE_TRANSCRIPT_DELTA = "response.audio_transcript.delta";
    static final String TYPE_OUTPUT_AUDIO_TRANSCRIPT_DELTA = "response.output_audio_transcript.delta";
    static final String TYPE_TEXT_DELTA = "response.text.delta";
    static final String TYPE_OUTPUT_TEXT_DELTA = "response.output_text.delta";
    static final String TYPE_RESPONSE_DONE = "response.done";
    static final String TYPE_SESSION_UPDATED = "session.updated";
    static final String TYPE_INPUT_TRANSCRIPT = "conversation.item.input_audio_transcription.completed";
    static final String TYPE_TRANSLATION_DELTA = "response.translation.delta";
    static final String TYPE_OUTPUT_TRANSLATION_DELTA = "response.output_text.translation.delta";
    static final String TYPE_FUNCTION_CALL_DONE = "response.function_call_arguments.done";
    static final String TYPE_RESPONSE_CREATE = "response.create";
    static final String TYPE_INPUT_COMMIT = "input_audio_buffer.commit";
    static final String TYPE_INPUT_CLEAR = "input_audio_buffer.clear";
    static final String TYPE_ITEM_TRUNCATE = "conversation.item.truncate";
    static final String TYPE_ITEM_CREATE = "conversation.item.create";
    static final String TYPE_ERROR = "error";

    static final String INPUT_AUDIO_FORMAT = "pcm16";
    static final String HEADER_AUTHORIZATION = "Authorization";
    static final String HEADER_OPENAI_BETA = "OpenAI-Beta";
    static final String OPENAI_BETA_VALUE = "realtime=v1";

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private RealtimeProtocol() {
    }

    @NonNull
    static JSONObject encodeSessionUpdate(@NonNull YoRealtimeConfig config) throws JSONException {
        JSONObject session = switch (config.dialect()) {
            case OPENAI_BETA -> encodeBetaSession(config);
            case OPENAI_GA -> encodeGaSession(config);
            case QWEN -> encodeQwenSession(config);
        };
        return new JSONObject().put("type", TYPE_SESSION_UPDATE).put("session", session);
    }

    @NonNull
    static JSONObject encodeAudioAppend(@NonNull byte[] pcm16) throws JSONException {
        return new JSONObject().put("type", TYPE_AUDIO_APPEND).put("audio", base64(pcm16));
    }

    @NonNull
    static JSONObject encodeResponseCancel() throws JSONException {
        return new JSONObject().put("type", TYPE_RESPONSE_CANCEL);
    }

    @NonNull
    static JSONObject encodeResponseCreate() throws JSONException {
        return new JSONObject().put("type", TYPE_RESPONSE_CREATE);
    }

    @NonNull
    static JSONObject encodeInputCommit() throws JSONException {
        return new JSONObject().put("type", TYPE_INPUT_COMMIT);
    }

    @NonNull
    static JSONObject encodeInputClear() throws JSONException {
        return new JSONObject().put("type", TYPE_INPUT_CLEAR);
    }

    @NonNull
    static JSONObject encodeTruncate(@NonNull String itemId, int contentIndex, int audioEndMs) throws JSONException {
        return new JSONObject()
                .put("type", TYPE_ITEM_TRUNCATE)
                .put("item_id", itemId)
                .put("content_index", Math.max(0, contentIndex))
                .put("audio_end_ms", Math.max(0, audioEndMs));
    }

    @NonNull
    static JSONObject encodeToolOutput(@NonNull String callId, @NonNull String output) throws JSONException {
        JSONObject item = new JSONObject()
                .put("type", "function_call_output")
                .put("call_id", callId)
                .put("output", output);
        return new JSONObject().put("type", TYPE_ITEM_CREATE).put("item", item);
    }

    @Nullable
    static Frame parse(@NonNull String text) throws YoAiProtocolException {
        JSONObject frame = objectOf(text);
        String type = frame.optString("type", "");
        return switch (type) {
            case TYPE_SESSION_CREATED -> new Frame.SessionCreated();
            case TYPE_SESSION_UPDATED -> new Frame.SessionUpdated();
            case TYPE_SPEECH_STARTED -> new Frame.Output(new YoRealtimeOutput.SpeechStarted());
            case TYPE_SPEECH_STOPPED -> new Frame.Output(new YoRealtimeOutput.SpeechStopped());
            case TYPE_AUDIO_DELTA, TYPE_OUTPUT_AUDIO_DELTA -> new Frame.Output(new YoRealtimeOutput.AssistantAudio(
                    decodeAudioDelta(frame, text), itemIdOf(frame), contentIndexOf(frame)));
            case TYPE_TRANSCRIPT_DELTA, TYPE_TEXT_DELTA,
                    TYPE_OUTPUT_AUDIO_TRANSCRIPT_DELTA, TYPE_OUTPUT_TEXT_DELTA -> {
                String delta = frame.optString("delta", "");
                yield delta.isEmpty() ? null : new Frame.Output(new YoRealtimeOutput.AssistantText(delta));
            }
            case TYPE_INPUT_TRANSCRIPT -> {
                String transcript = frame.optString("transcript", "");
                yield transcript.isEmpty() ? null
                        : new Frame.Output(new YoRealtimeOutput.UserTranscript(transcript, itemIdOf(frame)));
            }
            case TYPE_TRANSLATION_DELTA, TYPE_OUTPUT_TRANSLATION_DELTA -> {
                String delta = frame.optString("delta", "");
                if (delta.isEmpty()) {
                    yield null;
                }
                String target = frame.optString("target_language", "");
                yield new Frame.Output(new YoRealtimeOutput.TranslationDelta(
                        delta, target.isEmpty() ? null : target));
            }
            case TYPE_FUNCTION_CALL_DONE -> new Frame.Output(toolCallOf(frame));
            case TYPE_RESPONSE_DONE -> new Frame.Output(responseDoneOf(frame));
            case TYPE_ERROR -> new Frame.Output(errorOf(frame));
            default -> null;
        };
    }

    @NonNull
    static String webSocketUrl(@NonNull YoRealtimeConfig config) {
        if (config.dialect() == YoRealtimeDialect.QWEN) {
            return YoServiceEndpoints.qwenRealtime(config.baseUrl(), config.model());
        }
        return YoServiceEndpoints.realtime(config.baseUrl(), config.model());
    }

    @NonNull
    static Map<String, String> handshakeHeaders(@NonNull YoRealtimeConfig config) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_AUTHORIZATION, "Bearer " + config.apiKey());
        if (config.dialect() == YoRealtimeDialect.OPENAI_BETA) {
            headers.put(HEADER_OPENAI_BETA, OPENAI_BETA_VALUE);
        }
        return headers;
    }

    @NonNull
    static String base64(@NonNull byte[] data) {
        StringBuilder out = new StringBuilder(((data.length + 2) / 3) * 4);
        int index = 0;
        while (index + 2 < data.length) {
            int chunk = ((data[index] & 0xFF) << 16) | ((data[index + 1] & 0xFF) << 8) | (data[index + 2] & 0xFF);
            out.append(ALPHABET.charAt((chunk >> 18) & 0x3F))
                    .append(ALPHABET.charAt((chunk >> 12) & 0x3F))
                    .append(ALPHABET.charAt((chunk >> 6) & 0x3F))
                    .append(ALPHABET.charAt(chunk & 0x3F));
            index += 3;
        }
        int rest = data.length - index;
        if (rest == 1) {
            int chunk = (data[index] & 0xFF) << 16;
            out.append(ALPHABET.charAt((chunk >> 18) & 0x3F))
                    .append(ALPHABET.charAt((chunk >> 12) & 0x3F))
                    .append("==");
        } else if (rest == 2) {
            int chunk = ((data[index] & 0xFF) << 16) | ((data[index + 1] & 0xFF) << 8);
            out.append(ALPHABET.charAt((chunk >> 18) & 0x3F))
                    .append(ALPHABET.charAt((chunk >> 12) & 0x3F))
                    .append(ALPHABET.charAt((chunk >> 6) & 0x3F))
                    .append('=');
        }
        return out.toString();
    }

    @NonNull
    static byte[] unbase64(@NonNull String text) throws YoAiProtocolException {
        String clean = stripWhitespace(text);
        if (clean.isEmpty()) {
            return new byte[0];
        }
        if (clean.length() % 4 != 0) {
            throw new YoAiProtocolException("base64 长度不是 4 的倍数", text);
        }
        int padding = 0;
        if (clean.charAt(clean.length() - 1) == '=') {
            padding++;
            if (clean.length() >= 2 && clean.charAt(clean.length() - 2) == '=') {
                padding++;
            }
        }
        int payloadEnd = clean.length() - padding;
        byte[] out = new byte[(clean.length() / 4) * 3 - padding];
        int outIndex = 0;
        for (int base = 0; base < clean.length(); base += 4) {
            int chunk = 0;
            for (int offset = 0; offset < 4; offset++) {
                int position = base + offset;
                char symbol = clean.charAt(position);
                int value;
                if (position >= payloadEnd) {
                    if (symbol != '=') {
                        throw new YoAiProtocolException("base64 的填充字符位置不合法", text);
                    }
                    value = 0;
                } else {
                    value = alphabetValue(symbol);
                    if (value < 0) {
                        throw new YoAiProtocolException("base64 含非法字符 '" + symbol + "'", text);
                    }
                }
                chunk = (chunk << 6) | value;
            }
            if (outIndex < out.length) {
                out[outIndex++] = (byte) ((chunk >> 16) & 0xFF);
            }
            if (outIndex < out.length) {
                out[outIndex++] = (byte) ((chunk >> 8) & 0xFF);
            }
            if (outIndex < out.length) {
                out[outIndex++] = (byte) (chunk & 0xFF);
            }
        }
        return out;
    }

    @NonNull
    static YoAiErrorKind errorKindOf(
            @Nullable String type,
            @Nullable String code,
            @Nullable String message) {
        String haystack = (nullToEmpty(type) + ' ' + nullToEmpty(code) + ' ' + nullToEmpty(message))
                .toLowerCase(Locale.ROOT);
        if (containsAny(haystack, "api_key", "apikey", "authentication", "unauthorized", "invalid_key", "bearer")) {
            return YoAiErrorKind.AUTHENTICATION;
        }
        if (containsAny(haystack, "permission", "forbidden", "not_allowed", "not allowed")) {
            return YoAiErrorKind.PERMISSION;
        }
        if (containsAny(haystack, "quota", "billing", "insufficient")) {
            return YoAiErrorKind.QUOTA;
        }
        if (containsAny(haystack, "rate_limit", "rate limit", "too_many", "too many requests")) {
            return YoAiErrorKind.RATE_LIMITED;
        }
        if (containsAny(haystack, "not_found", "not found", "model_not_found", "unknown_model")) {
            return YoAiErrorKind.NOT_FOUND;
        }
        if (containsAny(haystack, "invalid_request", "invalid_value", "invalid_parameter",
                "bad_request", "missing_required")) {
            return YoAiErrorKind.BAD_REQUEST;
        }
        if (containsAny(haystack, "server_error", "internal", "overloaded", "unavailable")) {
            return YoAiErrorKind.SERVER;
        }
        return YoAiErrorKind.SERVER;
    }

    // ---------------------------------------------------------------- 会话编码

    @NonNull
    private static JSONObject encodeBetaSession(@NonNull YoRealtimeConfig config) throws JSONException {
        JSONObject session = new JSONObject();
        session.put("input_audio_format", betaFormatName(config.inputWireFormat()));
        session.put("output_audio_format", betaFormatName(config.outputWireFormat()));
        session.put("turn_detection", encodeTurn(config.turnDetection(), false));
        putIfPresent(session, "voice", config.voice());
        putIfPresent(session, "instructions", config.instructions());
        putBetaTranscription(session, config.transcription());
        putNoise(session, "input_audio_noise_reduction", config.noiseReduction());
        putModalities(session, "modalities", config.outputModalities());
        putTools(session, config);
        return session;
    }

    @NonNull
    private static JSONObject encodeGaSession(@NonNull YoRealtimeConfig config) throws JSONException {
        JSONObject session = new JSONObject();
        session.put("type", "realtime");
        session.put("model", config.model());
        session.put("output_modalities", modalityArray(config.outputModalities(), YoRealtimeOutputModality.AUDIO));
        putIfPresent(session, "instructions", config.instructions());
        JSONObject input = new JSONObject();
        input.put("format", encodeGaFormat(config.inputWireFormat()));
        input.put("turn_detection", encodeTurn(config.turnDetection(), true));
        putTranscription(input, config.transcription());
        putNoise(input, "noise_reduction", config.noiseReduction());
        JSONObject output = new JSONObject();
        output.put("format", encodeGaFormat(config.outputWireFormat()));
        putIfPresent(output, "voice", config.voice());
        session.put("audio", new JSONObject().put("input", input).put("output", output));
        putTools(session, config);
        return session;
    }

    @NonNull
    private static JSONObject encodeQwenSession(@NonNull YoRealtimeConfig config) throws JSONException {
        JSONObject session = new JSONObject();
        session.put("modalities", modalityArray(
                config.outputModalities(), YoRealtimeOutputModality.TEXT, YoRealtimeOutputModality.AUDIO));
        putIfPresent(session, "instructions", config.instructions());
        String voice = config.voice() == null ? "Tina" : config.voice();
        session.put("voice", voice);
        JSONObject input = new JSONObject();
        input.put("format", encodeQwenFormat(config.inputWireFormat()));
        input.put("turn_detection", encodeTurn(config.turnDetection(), false));
        putTranscription(input, config.transcription());
        JSONObject output = new JSONObject();
        output.put("format", encodeQwenFormat(config.outputWireFormat()));
        output.put("voice", voice);
        session.put("audio", new JSONObject().put("input", input).put("output", output));
        putTools(session, config);
        if (config.translationTarget() != null) {
            session.put("translation", new JSONObject().put("target_language", config.translationTarget()));
        }
        return session;
    }

    @NonNull
    private static String betaFormatName(@NonNull YoRealtimeAudioFormat format) {
        return switch (format.encoding()) {
            case PCM16 -> INPUT_AUDIO_FORMAT;
            case PCMU -> "g711_ulaw";
            case PCMA -> "g711_alaw";
        };
    }

    @NonNull
    private static JSONObject encodeGaFormat(@NonNull YoRealtimeAudioFormat format) throws JSONException {
        return switch (format.encoding()) {
            case PCM16 -> new JSONObject().put("type", "audio/pcm").put("rate", format.sampleRateHz());
            case PCMU -> new JSONObject().put("type", "audio/pcmu");
            case PCMA -> new JSONObject().put("type", "audio/pcma");
        };
    }

    @NonNull
    private static JSONObject encodeQwenFormat(@NonNull YoRealtimeAudioFormat format) throws JSONException {
        String type = switch (format.encoding()) {
            case PCM16 -> "pcm";
            case PCMU -> "pcmu";
            case PCMA -> "pcma";
        };
        return new JSONObject().put("type", type).put("sample_rate", format.sampleRateHz());
    }

    @NonNull
    private static Object encodeTurn(@Nullable YoRealtimeTurnDetection turn,
                                     boolean gaDefaults) throws JSONException {
        if (turn == null) {
            return encodeOpenAiTurn(new YoRealtimeTurnDetection.OpenAi(
                    YoRealtimeTurnDetection.Kind.SERVER_VAD, null, null, null, null, null, null),
                    gaDefaults);
        }
        return switch (turn) {
            case YoRealtimeTurnDetection.Disabled ignored -> JSONObject.NULL;
            case YoRealtimeTurnDetection.OpenAi openAi -> encodeOpenAiTurn(openAi, gaDefaults);
            case YoRealtimeTurnDetection.Qwen qwen -> encodeQwenTurn(qwen);
        };
    }

    @NonNull
    private static JSONObject encodeOpenAiTurn(@NonNull YoRealtimeTurnDetection.OpenAi turn,
                                               boolean gaDefaults) throws JSONException {
        YoRealtimeTurnDetection.Kind kind = turn.kind() == null
                ? YoRealtimeTurnDetection.Kind.SERVER_VAD
                : turn.kind();
        JSONObject json = new JSONObject().put("type", kind.wireName());
        if (turn.threshold() != null) {
            json.put("threshold", turn.threshold());
        }
        if (turn.prefixPaddingMs() != null) {
            json.put("prefix_padding_ms", turn.prefixPaddingMs());
        }
        if (turn.silenceDurationMs() != null) {
            json.put("silence_duration_ms", turn.silenceDurationMs());
        }
        if (turn.eagerness() != null) {
            json.put("eagerness", turn.eagerness());
        }
        if (gaDefaults) {
            json.put("create_response", turn.createResponse() == null || turn.createResponse());
            json.put("interrupt_response",
                    turn.interruptResponse() == null || turn.interruptResponse());
        } else {
            if (turn.createResponse() != null) {
                json.put("create_response", turn.createResponse());
            }
            if (turn.interruptResponse() != null) {
                json.put("interrupt_response", turn.interruptResponse());
            }
        }
        return json;
    }

    @NonNull
    private static JSONObject encodeQwenTurn(@NonNull YoRealtimeTurnDetection.Qwen turn)
            throws JSONException {
        JSONObject json = new JSONObject().put("type", turn.type());
        if (turn.threshold() != null) {
            json.put("threshold", turn.threshold());
        }
        if (turn.silenceDurationMs() != null) {
            json.put("silence_duration_ms", turn.silenceDurationMs());
        }
        if (turn.idleTimeoutMs() != null) {
            json.put("idle_timeout_ms", turn.idleTimeoutMs());
        }
        return json;
    }

    private static void putBetaTranscription(
            @NonNull JSONObject session,
            @Nullable YoRealtimeTranscription transcription) throws JSONException {
        if (transcription == null) {
            return;
        }
        session.put("input_audio_transcription", transcriptionJson(transcription));
    }

    private static void putTranscription(
            @NonNull JSONObject input,
            @Nullable YoRealtimeTranscription transcription) throws JSONException {
        if (transcription == null) {
            return;
        }
        input.put("transcription", transcriptionJson(transcription));
    }

    @NonNull
    private static JSONObject transcriptionJson(@NonNull YoRealtimeTranscription transcription) throws JSONException {
        JSONObject json = new JSONObject().put("model", transcription.model());
        putIfPresent(json, "language", transcription.language());
        putIfPresent(json, "prompt", transcription.prompt());
        return json;
    }

    private static void putNoise(
            @NonNull JSONObject parent,
            @NonNull String key,
            @Nullable YoRealtimeNoiseReduction noise) throws JSONException {
        if (noise == null) {
            return;
        }
        parent.put(key, new JSONObject().put("type", noise.wireName()));
    }

    private static void putModalities(
            @NonNull JSONObject session,
            @NonNull String key,
            @Nullable List<YoRealtimeOutputModality> modalities) throws JSONException {
        if (modalities == null || modalities.isEmpty()) {
            return;
        }
        session.put(key, modalityArray(modalities));
    }

    @NonNull
    private static JSONArray modalityArray(
            @Nullable List<YoRealtimeOutputModality> modalities,
            @NonNull YoRealtimeOutputModality... fallback) {
        JSONArray array = new JSONArray();
        if (modalities == null || modalities.isEmpty()) {
            for (YoRealtimeOutputModality modality : fallback) {
                array.put(modality.wireName());
            }
            return array;
        }
        for (YoRealtimeOutputModality modality : modalities) {
            array.put(modality.wireName());
        }
        return array;
    }

    private static void putTools(@NonNull JSONObject session, @NonNull YoRealtimeConfig config) throws JSONException {
        if (config.tools().isEmpty()) {
            return;
        }
        JSONArray tools = new JSONArray();
        for (YoRealtimeTool tool : config.tools()) {
            JSONObject encoded = new JSONObject().put("type", "function").put("name", tool.name());
            putIfPresent(encoded, "description", tool.description());
            encoded.put("parameters", tool.parameters());
            tools.put(encoded);
        }
        session.put("tools", tools);
    }

    private static void putIfPresent(
            @NonNull JSONObject json,
            @NonNull String key,
            @Nullable String value) throws JSONException {
        if (value != null) {
            json.put(key, value);
        }
    }

    @Nullable
    private static String itemIdOf(@NonNull JSONObject frame) {
        String id = frame.optString("item_id", "");
        return id.isEmpty() ? null : id;
    }

    private static int contentIndexOf(@NonNull JSONObject frame) {
        if (!frame.has("content_index") || frame.isNull("content_index")) {
            return 0;
        }
        return Math.max(0, frame.optInt("content_index", 0));
    }

    @NonNull
    private static YoRealtimeOutput.ToolCall toolCallOf(@NonNull JSONObject frame) {
        String callId = frame.optString("call_id", "");
        if (callId.isEmpty()) {
            callId = frame.optString("item_id", "");
        }
        String name = frame.optString("name", "");
        String arguments = frame.optString("arguments", "");
        return new YoRealtimeOutput.ToolCall(callId, name, arguments);
    }

    @NonNull
    private static YoRealtimeOutput.ResponseDone responseDoneOf(@NonNull JSONObject frame) {
        JSONObject response = frame.optJSONObject("response");
        String status = response == null ? "" : response.optString("status", "");
        JSONObject usage = response == null ? null : response.optJSONObject("usage");
        if (usage == null) {
            usage = frame.optJSONObject("usage");
        }
        return new YoRealtimeOutput.ResponseDone(status.isEmpty() ? null : status, readUsage(usage));
    }

    @NonNull
    private static JSONObject objectOf(@NonNull String text) throws YoAiProtocolException {
        if (text.isBlank()) {
            throw new YoAiProtocolException("WebSocket 帧是空的", text);
        }
        try {
            return new JSONObject(text);
        } catch (JSONException exception) {
            throw new YoAiProtocolException("WebSocket 帧不是 JSON 对象", text, exception);
        }
    }

    @NonNull
    private static byte[] decodeAudioDelta(@NonNull JSONObject frame, @NonNull String raw)
            throws YoAiProtocolException {
        String delta = frame.optString("delta", "");
        if (delta.isEmpty()) {
            throw new YoAiProtocolException("response.audio.delta 缺少 delta", raw);
        }
        return unbase64(delta);
    }

    @NonNull
    private static YoRealtimeOutput.Failed errorOf(@NonNull JSONObject frame) {
        JSONObject error = frame.optJSONObject("error");
        String type = error == null ? "" : error.optString("type", "");
        String code = error == null ? "" : scalarText(error.opt("code"));
        String message = error == null ? "" : error.optString("message", "");
        if (message.isBlank()) {
            message = frame.optString("message", "");
        }
        if (message.isBlank()) {
            message = code;
        }
        if (message.isBlank()) {
            message = type;
        }
        if (message.isBlank()) {
            message = "服务端返回了未说明的错误";
        }
        return new YoRealtimeOutput.Failed(errorKindOf(type, code, message), message);
    }

    @NonNull
    private static String scalarText(@Nullable Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        return String.valueOf(value);
    }

    @Nullable
    private static YoRealtimeUsage readUsage(@Nullable JSONObject usage) {
        if (usage == null) {
            return null;
        }
        int input = UsageFields.intOrZero(usage, "input_tokens", "prompt_tokens");
        int output = UsageFields.intOrZero(usage, "output_tokens", "completion_tokens");
        Integer total = UsageFields.firstInt(usage, "total_tokens");
        return new YoRealtimeUsage(
                input,
                output,
                total == null ? input + output : total,
                UsageFields.cachedTokens(usage),
                UsageFields.cost(usage));
    }

    @NonNull
    private static String stripWhitespace(@NonNull String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char symbol = value.charAt(index);
            if (symbol != ' ' && symbol != '\n' && symbol != '\r' && symbol != '\t') {
                out.append(symbol);
            }
        }
        return out.toString();
    }

    private static int alphabetValue(char symbol) {
        if (symbol >= 'A' && symbol <= 'Z') {
            return symbol - 'A';
        }
        if (symbol >= 'a' && symbol <= 'z') {
            return symbol - 'a' + 26;
        }
        if (symbol >= '0' && symbol <= '9') {
            return symbol - '0' + 52;
        }
        if (symbol == '+') {
            return 62;
        }
        if (symbol == '/') {
            return 63;
        }
        return -1;
    }

    private static boolean containsAny(@NonNull String haystack, @NonNull String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
