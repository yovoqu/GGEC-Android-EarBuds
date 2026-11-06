package com.yovo.lib_ai.speech;

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
import java.util.List;

/**
 * F12 STT 扩展：verbose_json / diarized_json 的 segments、words、speaker、usage，
 * 以及流式 SSE 拼接与 multipart 扩展字段。
 */
public final class TranscriptionExtTest {

    @Test
    public void verboseJsonParsesSegmentsWordsAndUsage() throws Exception {
        String raw = resource("/fixtures/f12/verbose.json");
        YoTranscription result = TranscriptionExtParser.decode(new JSONObject(raw), raw);

        assertEquals("你好，世界。", result.text());
        assertEquals("zh", result.language());
        assertEquals(2.5, result.durationSeconds(), 0.0001);

        assertEquals(2, result.segments().size());
        YoSegment first = result.segments().get(0);
        assertEquals("0", first.id());
        assertEquals(0.0, first.startSeconds(), 0.0001);
        assertEquals(1.2, first.endSeconds(), 0.0001);
        assertEquals("你好，", first.text());

        assertEquals(2, result.words().size());
        assertEquals("你好", result.words().get(0).word());
        assertEquals(0.6, result.words().get(0).endSeconds(), 0.0001);

        assertNotNull(result.usage());
        assertEquals(Integer.valueOf(12), result.usage().inputTokens());
        assertEquals(Integer.valueOf(8), result.usage().outputTokens());
        assertEquals(Integer.valueOf(20), result.usage().totalTokens());
        assertTrue(result.speakers().isEmpty());
    }

    @Test
    public void diarizedJsonKeepsSpeakerLabels() throws Exception {
        String raw = resource("/fixtures/f12/diarized.json");
        YoTranscription result = TranscriptionExtParser.decode(new JSONObject(raw), raw);

        assertEquals("Hello. Hi.", result.text());
        assertEquals(List.of("A", "B"), result.speakers());
        assertEquals("A", result.segments().get(0).speaker());
        assertEquals("B", result.segments().get(1).speaker());
        assertEquals("A", result.segments().get(0).words().get(0).speaker());
    }

    @Test
    public void streamDeltasConcatenateToDoneText() throws Exception {
        String sse = resource("/fixtures/f12/stream.sse");
        YoTranscription result = TranscriptionExtParser.parseStream(sse);

        assertEquals("Hello world.", result.text());
        assertNotNull(result.usage());
        assertEquals(Integer.valueOf(8), result.usage().totalTokens());
    }

    @Test
    public void multipartCarriesExtendedParts() {
        YoTranscriptionRequest request = YoTranscriptionRequest.builder()
                .wav(new byte[]{1, 2, 3, 4})
                .model("gpt-4o-transcribe")
                .responseFormat("verbose_json")
                .timestampGranularity("word")
                .timestampGranularity("segment")
                .stream(true)
                .diarizationEnabled(true)
                .knownSpeaker("张三")
                .build();

        String body = new String(SpeechMultipart.encode(request, "BOUNDARY"), StandardCharsets.UTF_8);

        assertTrue(body.contains("name=\"response_format\"\r\n\r\nverbose_json"));
        assertTrue(body.contains("name=\"timestamp_granularities[]\"\r\n\r\nword"));
        assertTrue(body.contains("name=\"timestamp_granularities[]\"\r\n\r\nsegment"));
        assertTrue(body.contains("name=\"stream\"\r\n\r\ntrue"));
        assertTrue(body.contains("name=\"chunking_strategy\"\r\n\r\nauto"));
        assertTrue(body.contains("name=\"known_speaker_names[]\"\r\n\r\n张三"));
    }

    @Test
    public void legacyConstructorLeavesExtensionFieldsEmpty() {
        YoTranscription legacy = new YoTranscription("t", "zh", 1.0, "{}");

        assertTrue(legacy.segments().isEmpty());
        assertTrue(legacy.words().isEmpty());
        assertTrue(legacy.speakers().isEmpty());
        assertNull(legacy.usage());
        assertEquals("t", legacy.text());
    }

    @Test
    public void plainJsonHasNoExtensionFields() throws Exception {
        String raw = "{\"text\":\"hello\",\"language\":\"en\",\"duration\":0.8}";
        YoTranscription result = TranscriptionExtParser.decode(new JSONObject(raw), raw);

        assertEquals("hello", result.text());
        assertTrue(result.segments().isEmpty());
        assertNull(result.usage());
        assertFalse(result.speakers().iterator().hasNext());
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = TranscriptionExtTest.class.getResourceAsStream(path)) {
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
