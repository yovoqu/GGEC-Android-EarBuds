package com.yovo.lib_ai.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * F24 Realtime 扩展：翻译事件、传输承载抽象。既有 WebSocket 用例不受影响（另有 RealtimeProtocolTest）。
 */
public final class RealtimeTranslationTest {

    @Test
    public void translationSseParsesIntoTranslationEvents() throws Exception {
        String sse = resource("/fixtures/f24/translation.sse");
        List<YoRealtimeEvent> events = new ArrayList<>();
        for (String line : sse.split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String payload = trimmed.substring(5).trim();
            if (payload.isEmpty()) {
                continue;
            }
            YoRealtimeEvent event = RealtimeProtocol.parse(payload);
            assertNotNull(event);
            events.add(event);
        }

        assertEquals(2, events.size());
        StringBuilder translated = new StringBuilder();
        for (YoRealtimeEvent event : events) {
            assertEquals(YoRealtimeEventType.TRANSLATION_DELTA, event.type());
            assertEquals("en", event.targetLanguage());
            translated.append(event.text());
        }
        assertEquals("Hello world", translated.toString());
    }

    @Test
    public void translationEventMapsToPublicOutput() throws Exception {
        YoRealtimeEvent event = RealtimeProtocol.parse(
                "{\"type\":\"response.translation.delta\",\"delta\":\"你好\",\"target_language\":\"zh\"}");

        assertNotNull(event);
        YoRealtimeOutput output = YoRealtimeOutputs.from(event);
        assertNotNull(output);
        assertTrue(output instanceof YoRealtimeOutput.TranslationDelta);
        YoRealtimeOutput.TranslationDelta delta = (YoRealtimeOutput.TranslationDelta) output;
        assertEquals("你好", delta.delta());
        assertEquals("zh", delta.targetLanguage());
    }

    @Test
    public void emptyTranslationDeltaIsDropped() throws Exception {
        assertEquals(null, RealtimeProtocol.parse(
                "{\"type\":\"response.translation.delta\",\"delta\":\"\"}"));
    }

    @Test
    public void webSocketIsDefaultTransportAndOthersArePlaceholders() {
        assertTrue(RealtimeTransport.defaultTransport().defaultEnabled());
        assertEquals("websocket", RealtimeTransport.defaultTransport().id());

        assertTrue(RealtimeTransport.webSocket().defaultEnabled());
        assertFalse(RealtimeTransport.webRtc().defaultEnabled());
        assertFalse(RealtimeTransport.aoq().defaultEnabled());
        assertEquals("webrtc", RealtimeTransport.webRtc().id());
        assertEquals("aoq", RealtimeTransport.aoq().id());
        assertEquals(3, RealtimeTransport.all().size());
    }

    @Test
    public void translationTargetIsOptionalAndEncodedOnlyForQwen() throws Exception {
        YoRealtimeConfig withTranslation = YoRealtimeConfig.builder()
                .baseUrl("https://dashscope.aliyuncs.com")
                .apiKey("sk-test")
                .model("qwen3.8-omni-flash-realtime")
                .dialect(YoRealtimeDialect.QWEN)
                .translationTarget("en")
                .build();
        assertEquals("en", withTranslation.translationTarget());

        JSONObject session = RealtimeProtocol.encodeSessionUpdate(withTranslation).getJSONObject("session");
        assertTrue(session.has("translation"));
        assertEquals("en", session.getJSONObject("translation").getString("target_language"));

        // 未设置时不写 translation，保持旧 JSON 字节不变（现有 beta 用例仍按旧字段断言）。
        YoRealtimeConfig plain = YoRealtimeConfig.builder()
                .baseUrl("https://api.example.com")
                .apiKey("sk-test")
                .model("gpt-4o-realtime-preview")
                .build();
        JSONObject betaSession = RealtimeProtocol.encodeSessionUpdate(plain).getJSONObject("session");
        assertFalse(betaSession.has("translation"));
        assertEquals(null, plain.translationTarget());
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = RealtimeTranslationTest.class.getResourceAsStream(path)) {
            assertNotNull("missing fixture " + path, stream);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
