package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiProtocolException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAI Realtime 兼容协议的线上格式。**只在这里写一次**，实现类和单测都用这里的函数。
 *
 * <p>把编解码从 {@link RealtimeWebSocketLink} 里拆出来的直接好处是：协议可以用纯 JSON 字符串测，
 * 不需要真的开一条连接。语音这块最容易错的是字段名和 base64，这两样都不该靠连真服务端才发现。</p>
 *
 * <p>包内可见（不是 public）：这是实现细节，上层只认 {@link YoRealtimeEvent}，
 * 换一个端点要改的字段名不该出现在公共 API 里。</p>
 */
final class RealtimeProtocol {

    /** 建会话：请求体是 {@code {"type":"session.update","session":{…}}}。 */
    static final String TYPE_SESSION_UPDATE = "session.update";
    /** 会话已建立。这是唯一会触发 {@code OPENED} 的帧。 */
    static final String TYPE_SESSION_CREATED = "session.created";
    /** 上行音频帧。 */
    static final String TYPE_AUDIO_APPEND = "input_audio_buffer.append";
    /** 上行：打断正在生成的回答。 */
    static final String TYPE_RESPONSE_CANCEL = "response.cancel";
    /** 服务端 VAD：人开口了。 */
    static final String TYPE_SPEECH_STARTED = "input_audio_buffer.speech_started";
    /** 服务端 VAD：人停下来了。 */
    static final String TYPE_SPEECH_STOPPED = "input_audio_buffer.speech_stopped";
    /** 下行音频增量，{@code delta} 是 base64 的 PCM16。 */
    static final String TYPE_AUDIO_DELTA = "response.audio.delta";
    /** 下行的回答文字增量（音频模态的转写）。 */
    static final String TYPE_TRANSCRIPT_DELTA = "response.audio_transcript.delta";
    /** 纯文本模态的回答增量，语义和上面一样，只是模态不同。 */
    static final String TYPE_TEXT_DELTA = "response.text.delta";
    /** 一轮回答结束。 */
    static final String TYPE_RESPONSE_DONE = "response.done";
    /** 服务端报错。 */
    static final String TYPE_ERROR = "error";

    /** Realtime 端点路径，接在平台基址后面（基址本身不含 {@code /v1}）。 */
    static final String REALTIME_PATH = "/v1/realtime";

    /** 上行格式。Realtime 只认这一档，采样率不在协议里表达。 */
    static final String INPUT_AUDIO_FORMAT = "pcm16";
    /** 下行格式。同上。 */
    static final String OUTPUT_AUDIO_FORMAT = "pcm16";
    /** 服务端一侧的断句方式：由服务端做 VAD，客户端不用自己算静音。 */
    static final String TURN_DETECTION_TYPE = "server_vad";

    static final String HEADER_AUTHORIZATION = "Authorization";
    static final String HEADER_OPENAI_BETA = "OpenAI-Beta";
    /** 这个头把网关切到 Realtime 的 beta 字段集；不发它，有的兼容网关会按新字段名回帧。 */
    static final String OPENAI_BETA_VALUE = "realtime=v1";

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private RealtimeProtocol() {
    }

    /**
     * {@code session.update}：一次性把音频格式、音色、提示词、断句方式写进会话。
     *
     * <p>为什么要显式下发 {@code pcm16}：服务端的默认输出格式是 {@code pcm16} 但输入格式在各家兼容网关
     * 上并不统一（有的默认 {@code g711_ulaw}）。不下发就会拿到一堆解不开的字节，听起来是噪声。</p>
     */
    @NonNull
    static JSONObject encodeSessionUpdate(@NonNull YoRealtimeConfig config) throws JSONException {
        JSONObject session = new JSONObject();
        session.put("input_audio_format", INPUT_AUDIO_FORMAT);
        session.put("output_audio_format", OUTPUT_AUDIO_FORMAT);
        session.put("turn_detection", new JSONObject().put("type", TURN_DETECTION_TYPE));
        if (config.voice() != null) {
            // 不下发就是服务端默认音色，别下发 null：null 会被当成非法音色名。
            session.put("voice", config.voice());
        }
        if (config.instructions() != null) {
            session.put("instructions", config.instructions());
        }
        return new JSONObject().put("type", TYPE_SESSION_UPDATE).put("session", session);
    }

    /**
     * {@code input_audio_buffer.append}。
     *
     * <p><b>为什么音频要 base64：</b>WebSocket 这一层我们只搬文本帧（服务端也只收文本帧），
     * 二进制得先变成文本。PCM16 是裸字节，直接按 UTF-8 塞进 JSON 会在任意字节上撞坏编码；
     * base64 把 3 字节压成 4 个可打印字符，代价是体积 ×4/3，换来的是「帧一定是合法 JSON」，
     * 并且不需要在 JSON 里处理转义。40KB/s 的音频折完约 53KB/s，一条家用连接完全吃得下。</p>
     */
    @NonNull
    static JSONObject encodeAudioAppend(@NonNull byte[] pcm16) throws JSONException {
        return new JSONObject().put("type", TYPE_AUDIO_APPEND).put("audio", base64(pcm16));
    }

    /**
     * {@code response.cancel}：用户插话时打断当前回答。
     */
    @NonNull
    static JSONObject encodeResponseCancel() throws JSONException {
        return new JSONObject().put("type", TYPE_RESPONSE_CANCEL);
    }

    /**
     * 把一帧文本解析成一个事件。
     *
     * @return 不需要上报的帧（未知类型、{@code session.updated}、{@code conversation.item.*} 等）返回 {@code null}，
     * 调用方直接忽略即可——**未知帧不能当成错误**，协议一直在加新帧，老客户端必须能继续跑。
     * @throws YoAiProtocolException 帧不是 JSON 对象，或者关键字段缺失/编码不对。
     * 这种帧说明对端不是 Realtime 端点，链路已经不可用，由调用方关掉。
     */
    @Nullable
    static YoRealtimeEvent parse(@NonNull String text) throws YoAiProtocolException {
        JSONObject frame = objectOf(text);
        String type = frame.optString("type", "");
        switch (type) {
            case TYPE_SESSION_CREATED:
                return YoRealtimeEvent.opened();
            case TYPE_SPEECH_STARTED:
                return YoRealtimeEvent.speechStarted();
            case TYPE_SPEECH_STOPPED:
                return YoRealtimeEvent.speechStopped();
            case TYPE_AUDIO_DELTA:
                return YoRealtimeEvent.audioChunk(decodeAudioDelta(frame, text));
            case TYPE_TRANSCRIPT_DELTA:
            case TYPE_TEXT_DELTA: {
                String delta = frame.optString("delta", "");
                // 空增量没有信息量，丢给上层只会让它的拼接逻辑多走一次。
                return delta.isEmpty() ? null : YoRealtimeEvent.textDelta(delta);
            }
            case TYPE_RESPONSE_DONE:
                return YoRealtimeEvent.responseDone();
            case TYPE_ERROR:
                return errorOf(frame);
            default:
                return null;
        }
    }

    /**
     * 平台基址 → WebSocket 地址：{@code wss://{host}/v1/realtime?model={model}}。
     *
     * <p>baseUrl 来自用户配置，是 {@code https://…} 的普通地址（聊天和转写都用它），
     * 这里把协议换掉而不是让用户再填一个 {@code wss://} 地址——同一台主机两次配置，早晚会不一致。</p>
     */
    @NonNull
    static String webSocketUrl(@NonNull YoRealtimeConfig config) {
        String base = config.baseUrl();
        String prefix;
        String host;
        if (base.startsWith("https://")) {
            prefix = "wss://";
            host = base.substring("https://".length());
        } else if (base.startsWith("http://")) {
            // 明文只在本机调试有意义；真机上必须 https，否则密钥会明文过网。
            prefix = "ws://";
            host = base.substring("http://".length());
        } else {
            throw new IllegalArgumentException("baseUrl must start with http:// or https://");
        }
        return prefix + host + REALTIME_PATH + "?model=" + encodeQueryValue(config.model());
    }

    /**
     * 握手头：Bearer 鉴权 + beta 通道标记。
     */
    @NonNull
    static Map<String, String> handshakeHeaders(@NonNull YoRealtimeConfig config) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_AUTHORIZATION, "Bearer " + config.apiKey());
        headers.put(HEADER_OPENAI_BETA, OPENAI_BETA_VALUE);
        return headers;
    }

    /**
     * 标准 base64（带 {@code =} 补齐，不换行）。
     *
     * <p>自己写而不是用 {@code java.util.Base64}：那是 API 26 才有的，而模块 minSdk 是 24；
     * {@code android.util.Base64} 在单元测试里是抛异常的桩，测不了。这段代码不依赖任何平台 API，
     * 单测里直接和 JDK 的 {@code java.util.Base64} 对拍。</p>
     */
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

    /**
     * 解 base64。
     *
     * <p>对空白宽容（有的网关会按行折行），但字母表以外的字符一律报错：
     * 音频宁可报一次协议错误，也不要解出一段看着像噪声的错位 PCM。</p>
     */
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

    /**
     * 服务端 {@code error} 帧的分类。
     *
     * <p>没有让调用方去猜 HTTP 状态码：Realtime 的错误在帧里，形状还不完全统一
     * （有的给 {@code code}，有的只给 {@code type} 和一句话）。所以这里把三者凑成一段文本做关键字匹配，
     * 顺序从「最确定」往下：密钥错了绝不能落到 {@code SERVER}，否则界面会让用户「稍后重试」一个永远好不了的问题。</p>
     */
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
        // 对端明确报了错，但没给出可识别的分类：归到服务端，原文照样交给界面显示。
        return YoAiErrorKind.SERVER;
    }

    // ---------------------------------------------------------------- 内部

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
    private static YoRealtimeEvent errorOf(@NonNull JSONObject frame) {
        JSONObject error = frame.optJSONObject("error");
        String type = error == null ? "" : error.optString("type", "");
        String code = error == null ? "" : scalarText(error.opt("code"));
        String message = error == null ? "" : error.optString("message", "");
        if (message.isBlank()) {
            // 少数网关把错误摊平在顶层。
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
        return YoRealtimeEvent.error(errorKindOf(type, code, message), message);
    }

    @NonNull
    private static String scalarText(@Nullable Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        return String.valueOf(value);
    }

    @NonNull
    private static String encodeQueryValue(@NonNull String value) {
        try {
            // 表单编码对 query 值合法（空格变 +），模型名里通常只有字母数字和连字符。
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 is always supported", exception);
        }
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
