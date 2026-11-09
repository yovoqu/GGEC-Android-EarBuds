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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 协议编解码与配置构建单测：只测 JSON 怎么拼、帧怎么解与默认方言解析。
 */
public final class RealtimeProtocolTest {

    @Test
    public void audioAppendUsesBase64OfRawPcm16() throws Exception {
        byte[] pcm = {0x00, 0x01, 0x02, (byte) 0xFF};

        JSONObject frame = RealtimeProtocol.encodeAudioAppend(pcm);

        assertEquals("input_audio_buffer.append", frame.getString("type"));
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
        assertEquals(1, session.getJSONObject("turn_detection").length());
        assertFalse(session.has("tools"));
        assertFalse(session.has("type"));
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
    public void sessionCreatedAndUpdatedReturnDirectControlFrames() throws Exception {
        RealtimeProtocol.Frame created = RealtimeProtocol.parse(
                "{\"type\":\"session.created\",\"session\":{\"id\":\"sess_1\"}}");
        RealtimeProtocol.Frame updated = RealtimeProtocol.parse(
                "{\"type\":\"session.updated\"}");

        assertInstanceOf(RealtimeProtocol.Frame.SessionCreated.class, created);
        assertInstanceOf(RealtimeProtocol.Frame.SessionUpdated.class, updated);
    }

    @Test
    public void mapsServerVadSpeechEvents() throws Exception {
        YoRealtimeOutput started = parseOutput(
                "{\"type\":\"input_audio_buffer.speech_started\",\"audio_start_ms\":120}");
        YoRealtimeOutput stopped = parseOutput(
                "{\"type\":\"input_audio_buffer.speech_stopped\",\"audio_end_ms\":980}");

        assertInstanceOf(YoRealtimeOutput.SpeechStarted.class, started);
        assertInstanceOf(YoRealtimeOutput.SpeechStopped.class, stopped);
    }

    @Test
    public void decodesAssistantAudioDelta() throws Exception {
        byte[] pcm = {0x10, 0x20, (byte) 0x80, (byte) 0xFF, 0x00, 0x01};
        String frame = "{\"type\":\"response.audio.delta\",\"response_id\":\"resp_1\",\"delta\":\""
                + Base64.getEncoder().encodeToString(pcm) + "\"}";

        YoRealtimeOutput.AssistantAudio audio =
                assertInstanceOf(YoRealtimeOutput.AssistantAudio.class, parseOutput(frame));

        assertArrayEquals(pcm, audio.pcm16());
        assertNull(audio.itemId());
        assertEquals(0, audio.contentIndex());
    }

    @Test
    public void mapsTranscriptDeltaOfBothModalities() throws Exception {
        YoRealtimeOutput.AssistantText audio = assertInstanceOf(
                YoRealtimeOutput.AssistantText.class,
                parseOutput("{\"type\":\"response.audio_transcript.delta\",\"delta\":\"你好\"}"));
        YoRealtimeOutput.AssistantText text = assertInstanceOf(
                YoRealtimeOutput.AssistantText.class,
                parseOutput("{\"type\":\"response.text.delta\",\"delta\":\"，我是\"}"));

        assertEquals("你好", audio.delta());
        assertEquals("，我是", text.delta());
    }

    @Test
    public void responseDoneMaps() throws Exception {
        YoRealtimeOutput.ResponseDone done = assertInstanceOf(
                YoRealtimeOutput.ResponseDone.class,
                parseOutput("{\"type\":\"response.done\",\"response\":{\"status\":\"cancelled\"}}"));

        assertEquals("cancelled", done.status());
    }

    @Test
    public void unknownAndIrrelevantFramesAreIgnored() throws Exception {
        assertNull(RealtimeProtocol.parse("{\"type\":\"conversation.item.created\"}"));
        assertNull(RealtimeProtocol.parse("{\"type\":\"rate_limits.updated\"}"));
        assertNull(RealtimeProtocol.parse("{\"foo\":1}"));
    }

    @Test
    public void nonJsonFrameIsProtocolError() {
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.parse("<html>502</html>"));
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.parse(""));
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.parse("[1,2,3]"));
    }

    @Test
    public void errorFrameBecomesFailedOutputWithClassification() throws Exception {
        YoRealtimeOutput.Failed auth = assertInstanceOf(
                YoRealtimeOutput.Failed.class,
                parseOutput("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\","
                        + "\"code\":\"invalid_api_key\",\"message\":\"Incorrect API key provided.\"}}"));
        YoRealtimeOutput.Failed server = assertInstanceOf(
                YoRealtimeOutput.Failed.class,
                parseOutput("{\"type\":\"error\",\"error\":{\"type\":\"server_error\",\"message\":\"内部错误\"}}"));
        YoRealtimeOutput.Failed flat = assertInstanceOf(
                YoRealtimeOutput.Failed.class,
                parseOutput("{\"type\":\"error\",\"message\":\"模型不支持实时语音\"}"));

        assertEquals(YoAiErrorKind.AUTHENTICATION, auth.kind());
        assertEquals("Incorrect API key provided.", auth.message());
        assertEquals(YoAiErrorKind.SERVER, server.kind());
        assertEquals("内部错误", server.message());
        assertEquals("模型不支持实时语音", flat.message());
    }

    @Test
    public void malformedAudioDeltaIsProtocolError() {
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
        byte[] data = "pcm16".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.getEncoder().encodeToString(data);

        assertArrayEquals(data, RealtimeProtocol.unbase64(" \n" + encoded + "\r\n"));
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.unbase64("****"));
        assertThrows(YoAiProtocolException.class, () -> RealtimeProtocol.unbase64("A=AA"));
    }

    @Test
    public void buildWebSocketUrlFromPlatformBaseUrl() {
        assertEquals("wss://api.example.com/v1/realtime?model=gpt-4o-realtime-preview",
                RealtimeProtocol.webSocketUrl(base().build()));
        assertEquals("wss://api.example.com/v1/realtime?model=m",
                RealtimeProtocol.webSocketUrl(base().baseUrl("https://api.example.com///").model("m").build()));
        assertEquals("ws://127.0.0.1:8080/v1/realtime?model=m",
                RealtimeProtocol.webSocketUrl(
                        base().baseUrl("http://127.0.0.1:8080").model("m").build()));
        assertEquals("wss://gw.example.com/v1/realtime?model=org%2Frt",
                RealtimeProtocol.webSocketUrl(
                        base().baseUrl("https://gw.example.com").model("org/rt").build()));
        assertEquals("wss://api.example.com/v1/realtime?model=m",
                RealtimeProtocol.webSocketUrl(
                        base().baseUrl("https://api.example.com/v1").model("m").build()));
    }

    @Test
    public void handshakeHeadersCarryBearerAndBetaChannel() {
        Map<String, String> headers = RealtimeProtocol.handshakeHeaders(base().build());

        assertEquals("Bearer sk-test", headers.get("Authorization"));
        assertEquals("realtime=v1", headers.get("OpenAI-Beta"));
    }

    @Test
    public void configResolvesWireFormatsPerDialect() {
        YoRealtimeConfig openAi = base().build();
        assertNull(openAi.voice());
        assertNull(openAi.instructions());
        assertEquals(YoRealtimeDialect.OPENAI_BETA, openAi.dialect());
        assertEquals(24_000, openAi.inputWireFormat().sampleRateHz());
        assertEquals(24_000, openAi.outputWireFormat().sampleRateHz());
        assertFalse(openAi.toString().contains("sk-test"));

        YoRealtimeConfig qwen = YoRealtimeConfig.builder()
                .baseUrl("https://dashscope.aliyuncs.com")
                .apiKey("sk-qwen")
                .model("qwen3.8-omni-flash-realtime")
                .build();
        assertEquals(YoRealtimeDialect.QWEN, qwen.dialect());
        assertEquals("自动推断 QWEN 方言时，默认上行线速率应为 16k",
                16_000, qwen.inputWireFormat().sampleRateHz());
        assertEquals(24_000, qwen.outputWireFormat().sampleRateHz());
        assertEquals("Tina", qwen.voice());
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
    }

    @Test
    public void baseUrlIsNormalizedWithoutTrailingSlashes() {
        YoRealtimeConfig config = base().baseUrl(" https://api.example.com/// ").build();

        assertEquals("https://api.example.com", config.baseUrl());
        assertTrue(config.toString().contains("https://api.example.com"));
    }

    @Test
    public void gaSessionDeclaresNestedRateAndFormat() throws Exception {
        JSONObject frame = RealtimeProtocol.encodeSessionUpdate(base()
                .dialect(YoRealtimeDialect.OPENAI_GA)
                .voice("marin")
                .build());

        JSONObject session = frame.getJSONObject("session");
        assertEquals("realtime", session.getString("type"));
        assertEquals("gpt-4o-realtime-preview", session.getString("model"));
        JSONObject input = session.getJSONObject("audio").getJSONObject("input");
        assertEquals("audio/pcm", input.getJSONObject("format").getString("type"));
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
        YoRealtimeOutput.AssistantAudio chunk =
                assertInstanceOf(YoRealtimeOutput.AssistantAudio.class, parseOutput(audio));
        assertEquals("item_9", chunk.itemId());
        assertEquals(0, chunk.contentIndex());
        assertArrayEquals(pcm, chunk.pcm16());

        YoRealtimeOutput.UserTranscript said = assertInstanceOf(
                YoRealtimeOutput.UserTranscript.class,
                parseOutput("{\"type\":\"conversation.item.input_audio_transcription.completed\","
                        + "\"item_id\":\"item_u\",\"transcript\":\"开灯\"}"));
        assertEquals("开灯", said.transcript());
        assertEquals("item_u", said.itemId());

        YoRealtimeOutput.ToolCall call = assertInstanceOf(
                YoRealtimeOutput.ToolCall.class,
                parseOutput("{\"type\":\"response.function_call_arguments.done\",\"call_id\":\"call_1\","
                        + "\"name\":\"set_volume\",\"arguments\":\"{\\\"level\\\":1}\"}"));
        assertEquals("set_volume", call.name());
        assertEquals("call_1", call.callId());
    }

    @Test
    public void responseDoneCarriesUsage() throws Exception {
        YoRealtimeOutput.ResponseDone event = assertInstanceOf(
                YoRealtimeOutput.ResponseDone.class,
                parseOutput("{\"type\":\"response.done\",\"response\":{\"status\":\"completed\",\"usage\":{"
                        + "\"input_tokens\":3,\"output_tokens\":4,\"total_tokens\":7}}}"));

        assertEquals("completed", event.status());
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

    private static YoRealtimeOutput parseOutput(String raw) throws YoAiProtocolException {
        RealtimeProtocol.Frame frame = RealtimeProtocol.parse(raw);
        RealtimeProtocol.Frame.Output output =
                assertInstanceOf(RealtimeProtocol.Frame.Output.class, frame);
        return output.output();
    }

    private static <T> T assertInstanceOf(Class<T> type, Object value) {
        assertTrue("expected " + type.getSimpleName() + " but got "
                + (value == null ? "null" : value.getClass().getSimpleName()),
                type.isInstance(value));
        return type.cast(value);
    }

    private static YoRealtimeConfig.Builder base() {
        return YoRealtimeConfig.builder()
                .baseUrl("https://api.example.com")
                .apiKey("sk-test")
                .model("gpt-4o-realtime-preview");
    }
}
