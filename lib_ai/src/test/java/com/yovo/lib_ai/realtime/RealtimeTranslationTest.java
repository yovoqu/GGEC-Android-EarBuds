package com.yovo.lib_ai.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * F24 Realtime 扩展：翻译事件直接解析、传输承载抽象。
 */
public final class RealtimeTranslationTest {

    @Test
    public void translationSseParsesDirectlyIntoPublicOutputs() throws Exception {
        String sse = resource("/fixtures/f24/translation.sse");
        List<YoRealtimeOutput.TranslationDelta> deltas = new ArrayList<>();
        for (String line : sse.split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String payload = trimmed.substring(5).trim();
            if (payload.isEmpty()) {
                continue;
            }
            RealtimeProtocol.Frame frame = RealtimeProtocol.parse(payload);
            RealtimeProtocol.Frame.Output output =
                    assertInstanceOf(RealtimeProtocol.Frame.Output.class, frame);
            YoRealtimeOutput.TranslationDelta delta =
                    assertInstanceOf(YoRealtimeOutput.TranslationDelta.class, output.output());
            deltas.add(delta);
        }

        assertEquals(2, deltas.size());
        StringBuilder translated = new StringBuilder();
        for (YoRealtimeOutput.TranslationDelta delta : deltas) {
            assertEquals("en", delta.targetLanguage());
            translated.append(delta.delta());
        }
        assertEquals("Hello world", translated.toString());
    }

    @Test
    public void emptyTranslationDeltaIsDropped() throws Exception {
        assertNull(RealtimeProtocol.parse(
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

        YoRealtimeConfig plain = YoRealtimeConfig.builder()
                .baseUrl("https://api.example.com")
                .apiKey("sk-test")
                .model("gpt-4o-realtime-preview")
                .build();
        JSONObject betaSession = RealtimeProtocol.encodeSessionUpdate(plain).getJSONObject("session");
        assertFalse(betaSession.has("translation"));
        assertNull(plain.translationTarget());
    }

    private static <T> T assertInstanceOf(Class<T> type, Object value) {
        assertTrue("expected " + type.getSimpleName() + " but got "
                + (value == null ? "null" : value.getClass().getSimpleName()),
                type.isInstance(value));
        return type.cast(value);
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
