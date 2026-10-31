package com.yovo.lib_ai.realtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiProtocolException;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Base64;
import java.util.Map;

/**
 * 只测协议：JSON 怎么拼、帧怎么解。
 *
 * <p>故意不测网络：WebSocket 的行为由 OkHttp 保证，在这里模拟一条连接只会得到一堆
 * 和真实网关无关的通过。真网关的差异（有的不回 {@code session.created}、有的把 error
 * 摊平在顶层）在下面这些用例里用「帧长什么样」表达，比 mock 连接更接近现场。</p>
 */
public final class RealtimeProtocolTest {

    @Test
    public void audioAppendUsesBase64OfRawPcm16() throws Exception {
        byte[] pcm = {0x00, 0x01, 0x02, (byte) 0xFF};

        JSONObject frame = RealtimeProtocol.encodeAudioAppend(pcm);

        assertEquals("input_audio_buffer.append", frame.getString("type"));
        // 用 JDK 的实现对拍：自己那份 base64 必须和服务端预期的一模一样。
        assertEquals(Base64.getEncoder().encodeToString(pcm), frame.getString("audio"));
        assertEquals("AAEC/w==", frame.getString("audio"));
    }

    @Test
    public void sessionUpdateDeclaresPcm16AndServerVad() throws Exception {
        JSONObject frame = RealtimeProtocol.encodeSessionUpdate(base().build());

        assertEquals("session.update", frame.getString("type"));
        JSONObject session = frame.getJSONObject("session");
        assertEquals("pcm16", session.getString("input_audio_format"));
        assertEquals("pcm16", session.getString("output_audio_format"));
        assertEquals("server_vad", session.getJSONObject("turn_detection").getString("type"));
        // 默认 beta 帧不能多字段：旧网关按「只看到 type」在跑，多写 create_response 会改它们的行为。
        assertEquals(1, session.getJSONObject("turn_detection").length());
        assertFalse(session.has("tools"));
        assertFalse(session.has("type"));
        // 没配就不下发：下发 null 会被当成非法音色名。
        assertFalse(session.has("voice"));
        assertFalse(session.has("instructions"));
    }

    @Test
    public void sessionUpdateCarriesVoiceAndInstructionsWhenConfigured() throws Exception {
        JSONObject frame = RealtimeProtocol.encodeSessionUpdate(base()
                .voice("alloy")
                .instructions("用中文，回答短一点。")
                .build());

        JSONObject session = frame.getJSONObject("session");
        assertEquals("alloy", session.getString("voice"));
        assertEquals("用中文，回答短一点。", session.getString("instructions"));
    }

    @Test
    public void blankVoiceIsDropped() throws Exception {
        // 空串音色会被服务端当成一个合法但不存在的声音名，整条会话都建不起来。
        JSONObject frame = RealtimeProtocol.encodeSessionUpdate(base().voice("   ").build());

        assertFalse(frame.getJSONObject("session").has("voice"));
    }

    @Test
    public void cancelResponseIsResponseCancel() throws Exception {
        JSONObject frame = RealtimeProtocol.encodeResponseCancel();

        assertEquals("response.cancel", frame.getString("type"));
        assertEquals(1, frame.length());
    }

    @Test
    public void sessionCreatedOpens() throws Exception {
        YoRealtimeEvent event = RealtimeProtocol.parse(
                "{\"type\":\"session.created\",\"session\":{\"id\":\"sess_1\"}}");

        assertNotNull(event);
        assertEquals(YoRealtimeEventType.OPENED, event.type());
        assertNull(event.text());
        assertNull(event.audioPcm16());
    }

    @Test
    public void mapsServerVadSpeechEvents() throws Exception {
        YoRealtimeEvent started = RealtimeProtocol.parse(
                "{\"type\":\"input_audio_buffer.speech_started\",\"audio_start_ms\":120}");
        YoRealtimeEvent stopped = RealtimeProtocol.parse(
                "{\"type\":\"input_audio_buffer.speech_stopped\",\"audio_end_ms\":980}");

        assertNotNull(started);
        assertNotNull(stopped);
        assertEquals(YoRealtimeEventType.INPUT_SPEECH_STARTED, started.type());
        assertEquals(YoRealtimeEventType.INPUT_SPEECH_STOPPED, stopped.type());
    }

    @Test
    public void decodesAssistantAudioDelta() throws Exception {
        byte[] pcm = {0x10, 0x20, (byte) 0x80, (byte) 0xFF, 0x00, 0x01};
        String frame = "{\"type\":\"response.audio.delta\",\"response_id\":\"resp_1\",\"delta\":\""
                + Base64.getEncoder().encodeToString(pcm) + "\"}";

        YoRealtimeEvent event = RealtimeProtocol.parse(frame);

        assertNotNull(event);
        assertEquals(YoRealtimeEventType.ASSISTANT_AUDIO_CHUNK, event.type());
        assertArrayEquals(pcm, event.audioPcm16());
    }

    @Test
    public void mapsTranscriptDeltaOfBothModalities() throws Exception {
        YoRealtimeEvent audio = RealtimeProtocol.parse(
                "{\"type\":\"response.audio_transcript.delta\",\"delta\":\"你好\"}");
        YoRealtimeEvent text = RealtimeProtocol.parse(
                "{\"type\":\"response.text.delta\",\"delta\":\"，我是\"}");

        assertNotNull(audio);
        assertNotNull(text);
        assertEquals(YoRealtimeEventType.ASSISTANT_TEXT_DELTA, audio.type());
        assertEquals("你好", audio.text());
        assertEquals(YoRealtimeEventType.ASSISTANT_TEXT_DELTA, text.type());
        assertEquals("，我是", text.text());
    }

    @Test
    public void responseDoneMaps() throws Exception {
        YoRealtimeEvent event = RealtimeProtocol.parse(
                "{\"type\":\"response.done\",\"response\":{\"status\":\"cancelled\"}}");

        assertNotNull(event);
        assertEquals(YoRealtimeEventType.RESPONSE_DONE, event.type());
    }

    @Test
    public void unknownAndIrrelevantFramesAreIgnored() throws Exception {
        // 协议一直在加帧：不认识的一律忽略，绝不能抛异常把链路带下去。
        assertNull(RealtimeProtocol.parse("{\"type\":\"conversation.item.created\"}"));
        YoRealtimeEvent updated = RealtimeProtocol.parse("{\"type\":\"session.updated\"}");
        assertNotNull(updated);
        assertEquals(YoRealtimeEventType.SESSION_UPDATED, updated.type());
        assertNull(RealtimeProtocol.parse("{\"type\":\"rate_limits.updated\"}"));
        // 连 type 都没有的帧：当作无关帧，不是错误。
        assertNull(RealtimeProtocol.parse("{\"foo\":1}"));
    }

    @Test
    public void nonJsonFrameIsProtocolError() {
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.parse("<html>502</html>"));
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.parse(""));
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.parse("[1,2,3]"));
    }

    @Test
    public void errorFrameBecomesErrorEventWithClassification() throws Exception {
        YoRealtimeEvent auth = RealtimeProtocol.parse(
                "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\","
                        + "\"code\":\"invalid_api_key\",\"message\":\"Incorrect API key provided.\"}}");
        YoRealtimeEvent server = RealtimeProtocol.parse(
                "{\"type\":\"error\",\"error\":{\"type\":\"server_error\",\"message\":\"内部错误\"}}");
        YoRealtimeEvent flat = RealtimeProtocol.parse(
                "{\"type\":\"error\",\"message\":\"模型不支持实时语音\"}");

        assertNotNull(auth);
        assertEquals(YoRealtimeEventType.ERROR, auth.type());
        // 密钥错了必须落到 AUTHENTICATION：界面不能把它显示成「稍后重试」。
        assertEquals(YoAiErrorKind.AUTHENTICATION, auth.errorKind());
        assertEquals("Incorrect API key provided.", auth.message());

        assertNotNull(server);
        assertEquals(YoAiErrorKind.SERVER, server.errorKind());
        assertEquals("内部错误", server.message());

        assertNotNull(flat);
        assertEquals(YoRealtimeEventType.ERROR, flat.type());
        assertEquals("模型不支持实时语音", flat.message());
    }

    @Test
    public void malformedAudioDeltaIsProtocolError() {
        // delta 缺失、或者不是 base64：宁可报协议错，也不要解出一段噪声去放。
        assertThrows(YoAiProtocolException.class,
                () -> RealtimeProtocol.parse("{\"type\":\"response.audio.delta\"}"));
        assertThrows(YoAiProtocolException.class,
                () -> RealtimeProtocol.parse("{\"type\":\"response.audio.delta\",\"delta\":\"@@@?\"}"));
        assertThrows(YoAiProtocolException.class,
                () -> RealtimeProtocol.parse("{\"type\":\"response.audio.delta\",\"delta\":\"AAA\"}"));
    }

    @Test
    public void base64MatchesJdkForEveryTail() throws Exception {
        for (int length = 0; length <= 24; length++) {
            byte[] data = new byte[length];
            for (int index = 0; index < length; index++) {
                data[index] = (byte) (index * 37 + 11);
            }

            assertEquals("length=" + length,
                    Base64.getEncoder().encodeToString(data), RealtimeProtocol.base64(data));
            assertArrayEquals("length=" + length, data,
                    RealtimeProtocol.unbase64(RealtimeProtocol.base64(data)));
        }
    }

    @Test
    public void unbase64ToleratesLineWrappingButNotGarbage() throws Exception {
        byte[] data = "pcm16".getBytes("UTF-8");
        String encoded = Base64.getEncoder().encodeToString(data);

        assertArrayEquals(data, RealtimeProtocol.unbase64(" \n" + encoded + "\r\n"));
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.unbase64("****"));
        // 填充字符跑到中间：长度对但位置不对，必须拒。
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.unbase64("A=AA"));
    }

    @Test
    public void buildWebSocketUrlFromPlatformBaseUrl() {
        assertEquals("wss://api.example.com/v1/realtime?model=gpt-4o-realtime-preview",
                RealtimeProtocol.webSocketUrl(base().build()));
        // 尾部斜杠要先归一化，否则会出现 /base//v1/realtime。
        assertEquals("wss://api.example.com/v1/realtime?model=m",
                RealtimeProtocol.webSocketUrl(base().baseUrl("https://api.example.com///").model("m").build()));
        // 本机明文调试。
        assertEquals("ws://127.0.0.1:8080/v1/realtime?model=m",
                RealtimeProtocol.webSocketUrl(
                        base().baseUrl("http://127.0.0.1:8080").model("m").build()));
        // 模型名里的 / 必须转义，否则 query 会被拆断。
        assertEquals("wss://gw.example.com/v1/realtime?model=org%2Frt",
                RealtimeProtocol.webSocketUrl(
                        base().baseUrl("https://gw.example.com").model("org/rt").build()));
    }

    @Test
    public void handshakeHeadersCarryBearerAndBetaChannel() {
        Map<String, String> headers = RealtimeProtocol.handshakeHeaders(base().build());

        assertEquals("Bearer sk-test", headers.get("Authorization"));
        assertEquals("realtime=v1", headers.get("OpenAI-Beta"));
    }

    @Test
    public void configDefaultsTo16kInputAnd24kOutput() {
        YoRealtimeConfig config = base().build();

        assertEquals(16_000, config.inputSampleRateHz());
        assertEquals(24_000, config.outputSampleRateHz());
        assertNull(config.voice());
        assertNull(config.instructions());
        assertEquals(YoRealtimeDialect.OPENAI_BETA, config.dialect());
        assertEquals(24_000, config.inputWireFormat().sampleRateHz());
        assertEquals(24_000, config.outputWireFormat().sampleRateHz());
        // 密钥不能出现在 toString 里：这个对象很容易被顺手打进日志。
        assertFalse(config.toString().contains("sk-test"));
    }

    @Test
    public void configRejectsIncompleteOrInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> YoRealtimeConfig.builder()
                .apiKey("sk-test").model("m").build());
        assertThrows(IllegalArgumentException.class, () -> YoRealtimeConfig.builder()
                .baseUrl("api.example.com").apiKey("sk-test").model("m").build());
        assertThrows(IllegalArgumentException.class, () -> YoRealtimeConfig.builder()
                .baseUrl("https://api.example.com").apiKey("  ").model("m").build());
        assertThrows(IllegalArgumentException.class, () -> YoRealtimeConfig.builder()
                .baseUrl("https://api.example.com").apiKey("k").model("").build());
        assertThrows(IllegalArgumentException.class, () -> base().inputSampleRateHz(0).build());
        assertThrows(IllegalArgumentException.class, () -> base().outputSampleRateHz(-1).build());
    }

    @Test
    public void baseUrlIsNormalizedWithoutTrailingSlashes() {
        YoRealtimeConfig config = base().baseUrl(" https://api.example.com/// ").build();

        assertEquals("https://api.example.com", config.baseUrl());
        assertTrue(config.toString().contains("https://api.example.com"));
    }

    @Test
    public void gaSessionDeclaresNestedRateAndDoesNotUseTheMicRate() throws Exception {
        JSONObject frame = RealtimeProtocol.encodeSessionUpdate(base()
                .dialect(YoRealtimeDialect.OPENAI_GA)
                .inputSampleRateHz(16_000)
                .voice("marin")
                .build());

        JSONObject session = frame.getJSONObject("session");
        assertEquals("realtime", session.getString("type"));
        assertEquals("gpt-4o-realtime-preview", session.getString("model"));
        JSONObject input = session.getJSONObject("audio").getJSONObject("input");
        assertEquals("audio/pcm", input.getJSONObject("format").getString("type"));
        // 麦克风是 16k，线上字节仍是默认 24k。把 16k 写进去，服务端会把升采样后的声音听慢。
        assertEquals(24_000, input.getJSONObject("format").getInt("rate"));
        JSONObject turn = input.getJSONObject("turn_detection");
        assertEquals("server_vad", turn.getString("type"));
        assertTrue(turn.getBoolean("interrupt_response"));
        assertEquals("marin", session.getJSONObject("audio").getJSONObject("output").getString("voice"));
        assertFalse(session.has("voice"));
    }

    @Test
    public void gaHandshakeOmitsBetaHeader() {
        Map<String, String> headers = RealtimeProtocol.handshakeHeaders(
                base().dialect(YoRealtimeDialect.OPENAI_GA).build());

        assertEquals("Bearer sk-test", headers.get("Authorization"));
        assertFalse(headers.containsKey("OpenAI-Beta"));
        assertTrue(YoRealtimeDialect.OPENAI_GA.holdsAudioUntilConfigured());
        assertFalse(YoRealtimeDialect.OPENAI_BETA.holdsAudioUntilConfigured());
    }

    @Test
    public void qwenSessionUsesSampleRateField() throws Exception {
        JSONObject session = RealtimeProtocol.encodeSessionUpdate(base()
                .dialect(YoRealtimeDialect.QWEN)
                .inputWireFormat(YoRealtimeAudioFormat.pcm16(16_000))
                .voice("Cherry")
                .build()).getJSONObject("session");

        assertEquals(16_000, session.getJSONObject("audio").getJSONObject("input")
                .getJSONObject("format").getInt("sample_rate"));
        assertEquals("pcm", session.getJSONObject("audio").getJSONObject("input")
                .getJSONObject("format").getString("type"));
        assertEquals("Cherry", session.getString("voice"));
        assertEquals("text", session.getJSONArray("modalities").getString(0));
        assertEquals("audio", session.getJSONArray("modalities").getString(1));
    }

    @Test
    public void parsesGaAudioAndUserTranscriptAndToolCall() throws Exception {
        byte[] pcm = {0x01, 0x02};
        String audio = "{\"type\":\"response.output_audio.delta\",\"item_id\":\"item_9\",\"content_index\":0,"
                + "\"delta\":\"" + Base64.getEncoder().encodeToString(pcm) + "\"}";
        YoRealtimeEvent chunk = RealtimeProtocol.parse(audio);
        assertNotNull(chunk);
        assertEquals("item_9", chunk.itemId());
        assertEquals(Integer.valueOf(0), chunk.contentIndex());
        assertArrayEquals(pcm, chunk.audioPcm16());

        YoRealtimeEvent said = RealtimeProtocol.parse(
                "{\"type\":\"conversation.item.input_audio_transcription.completed\","
                        + "\"item_id\":\"item_u\",\"transcript\":\"开灯\"}");
        assertNotNull(said);
        assertEquals(YoRealtimeEventType.USER_TRANSCRIPT, said.type());
        assertEquals("开灯", said.text());

        YoRealtimeEvent call = RealtimeProtocol.parse(
                "{\"type\":\"response.function_call_arguments.done\",\"call_id\":\"call_1\","
                        + "\"name\":\"set_volume\",\"arguments\":\"{\\\"level\\\":1}\"}");
        assertNotNull(call);
        assertEquals(YoRealtimeEventType.TOOL_CALL, call.type());
        assertEquals("set_volume", call.toolName());
        assertEquals("call_1", call.callId());
    }

    @Test
    public void responseDoneCarriesUsage() throws Exception {
        YoRealtimeEvent event = RealtimeProtocol.parse(
                "{\"type\":\"response.done\",\"response\":{\"status\":\"completed\",\"usage\":{"
                        + "\"input_tokens\":3,\"output_tokens\":4,\"total_tokens\":7}}}");

        assertNotNull(event);
        assertEquals("completed", event.responseStatus());
        assertNotNull(event.usage());
        assertEquals(3, event.usage().inputTokens());
        assertEquals(4, event.usage().outputTokens());
        assertEquals(7, event.usage().totalTokens());
    }

    @Test
    public void truncateAndToolOutputUseConversationItems() throws Exception {
        JSONObject truncate = RealtimeProtocol.encodeTruncate("item_1", 0, 250);
        assertEquals("conversation.item.truncate", truncate.getString("type"));
        assertEquals("item_1", truncate.getString("item_id"));
        assertEquals(250, truncate.getInt("audio_end_ms"));

        JSONObject tool = RealtimeProtocol.encodeToolOutput("call_1", "{\"ok\":true}");
        assertEquals("conversation.item.create", tool.getString("type"));
        assertEquals("function_call_output", tool.getJSONObject("item").getString("type"));
        assertEquals("call_1", tool.getJSONObject("item").getString("call_id"));
    }

    private static YoRealtimeConfig.Builder base() {
        return YoRealtimeConfig.builder()
                .baseUrl("https://api.example.com")
                .apiKey("sk-test")
                .model("gpt-4o-realtime-preview");
    }
}
