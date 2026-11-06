package com.yovo.lib_ai.speech;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiHttpException;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * F11 TTS 合成：请求编码、响应解码、二进制/SSE、错误映射。
 */
public final class SynthesisTest {

    private static final String API_KEY = "sk-test";

    // ---------------------------------------------------------------- 请求编码

    @Test
    public void encodesOpenAiRequestMatchingFixture() throws Exception {
        JSONObject expected = new JSONObject(resource("/fixtures/f11/openai_request.json"));
        JSONObject actual = YoSynthesisCodec.encode(openAiRequest());

        assertEquals(expected.getString("model"), actual.getString("model"));
        assertEquals(expected.getString("input"), actual.getString("input"));
        assertEquals(expected.getString("voice"), actual.getString("voice"));
        assertEquals(expected.getString("instructions"), actual.getString("instructions"));
        assertEquals(expected.getString("response_format"), actual.getString("response_format"));
        assertEquals(expected.getDouble("speed"), actual.getDouble("speed"), 0.0001);
        assertEquals(expected.getString("stream_format"), actual.getString("stream_format"));
    }

    @Test
    public void encodesQwenRequestMatchingFixture() throws Exception {
        JSONObject expected = new JSONObject(resource("/fixtures/f11/qwen_request.json"));
        JSONObject actual = YoSynthesisCodec.encode(qwenRequest());

        assertEquals(expected.getString("model"), actual.getString("model"));
        JSONObject expectedInput = expected.getJSONObject("input");
        JSONObject actualInput = actual.getJSONObject("input");
        assertEquals(expectedInput.getString("text"), actualInput.getString("text"));
        assertEquals(expectedInput.getString("voice"), actualInput.getString("voice"));
        assertEquals(expectedInput.getString("format"), actualInput.getString("format"));
        assertEquals(expectedInput.getInt("sample_rate"), actualInput.getInt("sample_rate"));
    }

    @Test
    public void rejectsSpeedOutOfRangeLocally() {
        try {
            YoSynthesisRequest.builder().input("hi").model("m").voice("alloy").speed(5.0);
            fail("expected IllegalArgumentException for speed > 4.0");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("speed"));
        }
    }

    @Test
    public void requiresInputAndModel() {
        try {
            YoSynthesisRequest.builder().model("m").build();
            fail("expected input required");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("input"));
        }
        try {
            YoSynthesisRequest.builder().input("hi").build();
            fail("expected model required");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("model"));
        }
    }

    // ---------------------------------------------------------------- 响应解码

    @Test
    public void decodesQwenUrlResponse() throws Exception {
        YoSynthesisResult result = YoSynthesisCodec.decodeQwen(
                resource("/fixtures/f11/qwen_response.json"), null);

        assertFalse(result.hasAudio());
        assertEquals("https://dashscope-result.oss-cn-beijing.aliyuncs.com/tts/out.wav", result.audioUrl());
        assertEquals("req_tts_1", result.requestId());
    }

    @Test
    public void decodesOpenAiBinary() {
        byte[] wav = new byte[]{'R', 'I', 'F', 'F', 1, 2, 3, 4};

        YoSynthesisResult result = YoSynthesisCodec.decodeOpenAiBinary(wav, "audio/wav", "req_1", "wav");

        assertTrue(result.hasAudio());
        assertArrayEquals(wav, result.audioBytes());
        assertEquals("audio/wav", result.mimeType());
        assertEquals("req_1", result.requestId());
    }

    @Test
    public void decodesQwenBase64AudioToRiffBytes() {
        byte[] wav = new byte[]{'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E'};
        String raw = "{\"request_id\":\"req_b\",\"output\":{\"audio\":{\"data\":\""
                + Base64.getEncoder().encodeToString(wav) + "\"}}}";

        YoSynthesisResult result = YoSynthesisCodec.decodeQwen(raw, null);

        assertTrue(result.hasAudio());
        assertArrayEquals(wav, result.audioBytes());
        assertEquals("audio/wav", result.mimeType());
    }

    @Test
    public void decodesOpenAiSseConcatenatesDeltas() {
        byte[] head = new byte[]{'R', 'I', 'F', 'F'};
        byte[] tail = new byte[]{1, 2, 3, 4, 5, 6, 7};
        String sse = "event: speech.audio.delta\n"
                + "data: {\"type\":\"speech.audio.delta\",\"audio\":\""
                + Base64.getEncoder().encodeToString(head) + "\"}\n\n"
                + "event: speech.audio.delta\n"
                + "data: {\"type\":\"speech.audio.delta\",\"audio\":\""
                + Base64.getEncoder().encodeToString(tail) + "\"}\n\n"
                + "event: speech.audio.done\n"
                + "data: {\"type\":\"speech.audio.done\"}\n\n";

        YoSynthesisResult result = YoSynthesisCodec.decodeOpenAiSse(sse, null);

        byte[] joined = new byte[head.length + tail.length];
        System.arraycopy(head, 0, joined, 0, head.length);
        System.arraycopy(tail, 0, joined, head.length, tail.length);
        assertArrayEquals(joined, result.audioBytes());
    }

    // ---------------------------------------------------------------- 端到端（假传输）

    @Test
    public void synthesizeOpenAiReturnsBinaryAndHitsSpeechEndpoint() {
        byte[] audio = new byte[]{'R', 'I', 'F', 'F', 9, 9};
        FakeTransport transport = new FakeTransport()
                .reply(new SynthesisTransport.Response(200, "req_ok", audio, "audio/wav"));
        YoSpeechSynthesis synthesis = new YoSpeechSynthesis(transport);

        YoSynthesisResult result = synthesis.synthesize("https://api.openai.com", API_KEY, openAiRequest());

        assertTrue(result.hasAudio());
        assertArrayEquals(audio, result.audioBytes());
        assertEquals("https://api.openai.com/v1/audio/speech", transport.onlyUrl());
        assertEquals("Bearer " + API_KEY, transport.onlyHeaders().get("Authorization"));
        assertTrue(new String(transport.onlyBody(), StandardCharsets.UTF_8).contains("\"input\""));
    }

    @Test
    public void synthesizeQwenReturnsRemoteUrl() throws Exception {
        FakeTransport transport = new FakeTransport()
                .reply(new SynthesisTransport.Response(200, "req_q", resource(
                        "/fixtures/f11/qwen_response.json").getBytes(StandardCharsets.UTF_8),
                        "application/json"));
        YoSpeechSynthesis synthesis = new YoSpeechSynthesis(transport);

        YoSynthesisResult result = synthesis.synthesize(
                "https://dashscope.aliyuncs.com", API_KEY, qwenRequest());

        assertEquals("https://dashscope-result.oss-cn-beijing.aliyuncs.com/tts/out.wav", result.audioUrl());
        assertEquals("https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer",
                transport.onlyUrl());
    }

    @Test
    public void mapsUnauthorizedToAuthentication() {
        FakeTransport transport = new FakeTransport()
                .reply(new SynthesisTransport.Response(401, "req_1",
                        "{\"error\":{\"message\":\"bad key\",\"type\":\"authentication_error\"}}".getBytes(
                                StandardCharsets.UTF_8),
                        "application/json"));
        YoSpeechSynthesis synthesis = new YoSpeechSynthesis(transport);

        try {
            synthesis.synthesize("https://api.openai.com", API_KEY, openAiRequest());
            fail("expected failure");
        } catch (YoAiException failure) {
            assertTrue(failure instanceof YoAiHttpException);
            assertEquals(401, ((YoAiHttpException) failure).statusCode());
            assertEquals("bad key", failure.getMessage());
            assertEquals(YoAiErrorKind.AUTHENTICATION, YoAiErrors.kindOf(failure));
        }
    }

    @Test
    public void streamInvokesAudioThenComplete() {
        byte[] audio = new byte[]{'R', 'I', 'F', 'F', 1};
        FakeTransport transport = new FakeTransport()
                .reply(new SynthesisTransport.Response(200, "req_ok", audio, "audio/wav"));
        YoSpeechSynthesis synthesis = new YoSpeechSynthesis(transport);
        List<byte[]> chunks = new ArrayList<>();
        List<YoSynthesisResult> completed = new ArrayList<>();

        synthesis.stream("https://api.openai.com", API_KEY, openAiRequest(), new YoSynthesisStreamListener() {
            @Override
            public void onAudio(byte[] chunk) {
                chunks.add(chunk);
            }

            @Override
            public void onComplete(YoSynthesisResult result) {
                completed.add(result);
            }

            @Override
            public void onFailure(YoAiException error) {
                fail("unexpected failure: " + error.getMessage());
            }
        });

        assertEquals(1, chunks.size());
        assertArrayEquals(audio, chunks.get(0));
        assertEquals(1, completed.size());
    }

    // ---------------------------------------------------------------- 辅助

    private static YoSynthesisRequest openAiRequest() {
        return YoSynthesisRequest.builder()
                .dialect(YoSynthesisRequest.Dialect.OPENAI)
                .model("gpt-4o-mini-tts")
                .input("你好，欢迎使用。")
                .voice("alloy")
                .instructions("用温和的语气朗读")
                .responseFormat("wav")
                .speed(1.0)
                .streamFormat("sse")
                .build();
    }

    private static YoSynthesisRequest qwenRequest() {
        return YoSynthesisRequest.builder()
                .dialect(YoSynthesisRequest.Dialect.QWEN)
                .model("qwen-audio-3.1-tts-flash")
                .input("你好，欢迎使用阿里云百炼。")
                .voice("longanhuan_v3.1")
                .format("wav")
                .sampleRate(24000)
                .build();
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = SynthesisTest.class.getResourceAsStream(path)) {
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

    private static final class FakeTransport implements SynthesisTransport {

        private final ArrayDeque<SynthesisTransport.Response> replies = new ArrayDeque<>();
        private final List<String> urls = new ArrayList<>();
        private final List<Map<String, String>> headers = new ArrayList<>();
        private final List<byte[]> bodies = new ArrayList<>();

        FakeTransport reply(SynthesisTransport.Response response) {
            replies.add(response);
            return this;
        }

        String onlyUrl() {
            assertEquals("应当只发一次请求", 1, urls.size());
            return urls.get(0);
        }

        Map<String, String> onlyHeaders() {
            return headers.get(0);
        }

        byte[] onlyBody() {
            return bodies.get(0);
        }

        @Override
        public SynthesisTransport.Response post(
                String url, Map<String, String> requestHeaders, byte[] body, String contentType) {
            urls.add(url);
            headers.add(Map.copyOf(requestHeaders));
            bodies.add(body.clone());
            SynthesisTransport.Response response = replies.poll();
            if (response == null) {
                throw new AssertionError("unexpected extra request to " + url);
            }
            return response;
        }

        @Override
        public void close() {
        }
    }
}
