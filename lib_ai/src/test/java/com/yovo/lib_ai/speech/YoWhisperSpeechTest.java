package com.yovo.lib_ai.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.error.YoAiTransportException;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * 转写链路的单测。
 *
 * <p>两种驱动方式，各管一段：
 * <ul>
 *   <li>假 {@link SpeechTransport}：直接喂状态码/异常，钉住"错误映射"与"是否重试"，
 *       不需要网络，也不会因为本机端口被占用而随机失败。</li>
 *   <li>{@link MockWebServer}：跑真的 OkHttp 实现，钉住实际发出的请求行、请求头与
 *       multipart 字节——假实现看不到"到底发了什么"。</li>
 * </ul>
 *
 * <p>这些测试不需要改动别人的类：lib_ai 原本没有可注入的传输抽象
 * （{@code OkHttpTransport} 是具体类且写死 JSON），所以本包自己留了一个包内可见的缝。
 */
public final class YoWhisperSpeechTest {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String API_KEY = "sk-test";

    // ---------------------------------------------------------------- 请求构造

    @Test
    public void sendsMultipartWavWithBearerAuth() {
        FakeTransport transport = new FakeTransport()
                .reply(ok("{\"text\":\"你好。\",\"language\":\"zh\",\"duration\":1.25}"));
        YoWhisperSpeech speech = speech(transport, 0);
        byte[] pcm = new byte[]{1, 2, 3, 4, 5, 6};

        YoTranscription result = speech.transcribe(BASE_URL, API_KEY, YoTranscriptionRequest.builder()
                .pcm16(pcm)
                .model("whisper-1")
                .language("zh")
                .prompt("YoAi 耳机")
                .build());

        assertEquals("你好。", result.text());
        assertEquals("zh", result.language());
        assertNotNull(result.durationSeconds());
        assertEquals(1.25, result.durationSeconds(), 0.0001);
        assertTrue(result.rawJson().contains("你好。"));

        RecordedCall call = transport.onlyCall();
        assertEquals(BASE_URL + "/v1/audio/transcriptions", call.url);
        assertEquals("Bearer " + API_KEY, call.headers.get("Authorization"));
        assertTrue(call.contentType.startsWith("multipart/form-data; boundary="));
        String boundary = call.contentType.substring("multipart/form-data; boundary=".length());
        assertFalse(boundary.isBlank());

        String body = new String(call.body, StandardCharsets.UTF_8);
        assertTrue(body.contains("name=\"file\"; filename=\"" + YoTranscriptionRequest.FILENAME + "\""));
        assertTrue(body.contains("Content-Type: audio/wav"));
        assertTrue(body.contains("name=\"model\"\r\n\r\nwhisper-1"));
        assertTrue(body.contains("name=\"response_format\"\r\n\r\njson"));
        assertTrue(body.contains("name=\"language\"\r\n\r\nzh"));
        assertTrue(body.contains("name=\"prompt\"\r\n\r\nYoAi 耳机"));
        assertTrue(body.endsWith("--" + boundary + "--\r\n"));
        // 裸 PCM 必须在发送前包上 44 字节头，否则服务端无法从字节里认出格式。
        assertTrue("上传体应包含封装后的 wav",
                contains(call.body, WavEncoder.encode(pcm)));
    }

    @Test
    public void omitsOptionalPartsAndSendsWavAsIs() {
        FakeTransport transport = new FakeTransport().reply(ok("{\"text\":\"\"}"));
        YoWhisperSpeech speech = speech(transport, 0);
        byte[] wav = WavEncoder.encode(new byte[]{9, 8, 7, 6});

        speech.transcribe(BASE_URL, API_KEY, YoTranscriptionRequest.wav(wav, "whisper-1"));

        RecordedCall call = transport.onlyCall();
        String body = new String(call.body, StandardCharsets.UTF_8);
        assertFalse("没给 language 就不该发这个 part", body.contains("name=\"language\""));
        assertFalse("没给 prompt 就不该发这个 part", body.contains("name=\"prompt\""));
        assertTrue("已经是 wav 的字节应原样上传", contains(call.body, wav));
    }

    @Test
    public void doesNotDuplicateVersionSegmentInUrl() {
        FakeTransport transport = new FakeTransport().reply(ok("{\"text\":\"ok\"}"));
        YoWhisperSpeech speech = speech(transport, 0);

        speech.transcribe(BASE_URL + "/v1", API_KEY, request());

        assertEquals(BASE_URL + "/v1/audio/transcriptions", transport.onlyCall().url);
    }

    // ---------------------------------------------------------------- 结果解码

    @Test
    public void treatsEmptyTextAsSilence() {
        YoWhisperSpeech speech = speech(new FakeTransport().reply(ok("{\"text\":\"\"}")), 0);

        YoTranscription result = speech.transcribe(BASE_URL, API_KEY, request());

        assertEquals("", result.text());
        assertNull(result.language());
        assertNull(result.durationSeconds());
    }

    @Test
    public void acceptsDurationGivenAsString() {
        YoWhisperSpeech speech = speech(
                new FakeTransport().reply(ok("{\"text\":\"a\",\"duration\":\"2.5\"}")), 0);

        assertEquals(2.5, speech.transcribe(BASE_URL, API_KEY, request()).durationSeconds(), 0.0001);
    }

    @Test
    public void failsWhenResponseHasNoTextField() {
        YoWhisperSpeech speech = speech(
                new FakeTransport().reply(ok("{\"language\":\"zh\"}")), 0);

        YoAiException failure = failure(speech);
        assertTrue(failure instanceof YoAiProtocolException);
        assertEquals(YoAiErrorKind.PROTOCOL, YoAiErrors.kindOf(failure));
    }

    @Test
    public void failsWhenResponseIsNotJson() {
        YoWhisperSpeech speech = speech(new FakeTransport().reply(ok("<html>oops</html>")), 0);

        assertEquals(YoAiErrorKind.PROTOCOL, YoAiErrors.kindOf(failure(speech)));
    }

    // ---------------------------------------------------------------- 错误映射

    @Test
    public void mapsUnauthorizedWithoutRetrying() {
        FakeTransport transport = new FakeTransport().reply(new SpeechTransport.Response(
                401,
                "req_1",
                "{\"error\":{\"message\":\"bad key\",\"type\":\"authentication_error\"}}",
                null));
        YoWhisperSpeech speech = speech(transport, 2);

        YoAiException failure = failure(speech);

        assertTrue(failure instanceof YoAiHttpException);
        YoAiHttpException http = (YoAiHttpException) failure;
        assertEquals(401, http.statusCode());
        assertEquals("req_1", http.requestId());
        assertEquals("bad key", http.getMessage());
        assertEquals(YoAiErrorKind.AUTHENTICATION, YoAiErrors.kindOf(failure));
        assertEquals("鉴权失败重试没有意义", 1, transport.calls.size());
        assertFalse("错误体里不该出现 API Key",
                String.valueOf(http.responseBody()).contains(API_KEY));
    }

    @Test
    public void retriesRateLimitThenSucceeds() {
        FakeTransport transport = new FakeTransport()
                .reply(new SpeechTransport.Response(
                        429,
                        "req_2",
                        "{\"error\":{\"message\":\"slow down\",\"type\":\"rate_limit\"}}",
                        0L))
                .reply(ok("{\"text\":\"好\"}"));
        YoWhisperSpeech speech = speech(transport, 1);

        assertEquals("好", speech.transcribe(BASE_URL, API_KEY, request()).text());
        assertEquals(2, transport.calls.size());
    }

    @Test
    public void reportsRateLimitedAfterRetriesExhausted() {
        FakeTransport transport = new FakeTransport()
                .reply(new SpeechTransport.Response(429, null, "{}", 0L))
                .reply(new SpeechTransport.Response(429, null, "{}", 0L));
        YoWhisperSpeech speech = speech(transport, 1);

        assertEquals(YoAiErrorKind.RATE_LIMITED, YoAiErrors.kindOf(failure(speech)));
        assertEquals(2, transport.calls.size());
    }

    @Test
    public void retriesServerErrorThenReportsServer() {
        FakeTransport transport = new FakeTransport()
                .reply(new SpeechTransport.Response(503, null, "", 0L))
                .reply(new SpeechTransport.Response(500, null, "{\"message\":\"boom\"}", 0L));
        YoWhisperSpeech speech = speech(transport, 1);

        YoAiException failure = failure(speech);
        assertEquals(YoAiErrorKind.SERVER, YoAiErrors.kindOf(failure));
        assertEquals("boom", failure.getMessage());
        assertEquals(2, transport.calls.size());
    }

    @Test
    public void mapsReadTimeoutAndDoesNotRetryIt() {
        FakeTransport transport = new FakeTransport()
                .fail(new YoAiTransportException("timeout", new SocketTimeoutException("read timed out")))
                .reply(ok("{\"text\":\"不会走到这里\"}"));
        YoWhisperSpeech speech = speech(transport, 2);

        assertEquals(YoAiErrorKind.TIMEOUT, YoAiErrors.kindOf(failure(speech)));
        assertEquals("读超时已经吃满读预算，再等一轮只会让通话页更久", 1, transport.calls.size());
    }

    @Test
    public void retriesNetworkFailureThenReportsNetwork() {
        FakeTransport transport = new FakeTransport()
                .fail(new YoAiTransportException("refused", new ConnectException("connection refused")))
                .fail(new YoAiTransportException("refused", new ConnectException("connection refused")));
        YoWhisperSpeech speech = speech(transport, 1);

        assertEquals(YoAiErrorKind.NETWORK, YoAiErrors.kindOf(failure(speech)));
        assertEquals(2, transport.calls.size());
    }

    @Test
    public void mapsCancelledTransportWithoutRetrying() {
        FakeTransport transport = new FakeTransport()
                .fail(YoAiTransportException.cancelled(new IOException("Canceled")));
        YoWhisperSpeech speech = speech(transport, 2);

        assertEquals(YoAiErrorKind.CANCELLED, YoAiErrors.kindOf(failure(speech)));
        assertEquals(1, transport.calls.size());
    }

    // ---------------------------------------------------------------- 入参与生命周期

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankApiKeyBeforeSending() {
        FakeTransport transport = new FakeTransport();
        try {
            speech(transport, 0).transcribe(BASE_URL, "  ", request());
        } finally {
            assertEquals(0, transport.calls.size());
        }
    }

    @Test
    public void refusesWorkAfterClose() {
        FakeTransport transport = new FakeTransport();
        YoWhisperSpeech speech = speech(transport, 0);
        speech.close();
        assertTrue(transport.closed);
        try {
            speech.transcribe(BASE_URL, API_KEY, request());
            throw new AssertionError("closed instance should not send");
        } catch (IllegalStateException expected) {
            assertEquals(0, transport.calls.size());
        }
    }

    // ---------------------------------------------------------------- 真实 HTTP

    @Test
    public void roundTripsOverRealHttp() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("{\"text\":\"真实网络\",\"language\":\"zh\"}"));
            try (YoWhisperSpeech speech = YoWhisperSpeech.builder()
                    .initialRetryDelayMillis(0)
                    .build()) {
                YoTranscription result = speech.transcribe(
                        server.url("/").toString(),
                        API_KEY,
                        YoTranscriptionRequest.pcm16(new byte[]{0, 0, 0, 0}, "whisper-1"));

                assertEquals("真实网络", result.text());
                RecordedRequest recorded = server.takeRequest();
                assertEquals("/v1/audio/transcriptions", recorded.getPath());
                assertEquals("Bearer " + API_KEY, recorded.getHeader("Authorization"));
                assertTrue(recorded.getHeader("Content-Type").startsWith("multipart/form-data; boundary="));
                assertTrue(recorded.getBody().readUtf8()
                        .contains("name=\"file\"; filename=\"" + YoTranscriptionRequest.FILENAME + "\""));
            }
        }
    }

    @Test
    public void retriesRateLimitOverRealHttp() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "0")
                    .setBody("{\"error\":{\"message\":\"slow down\",\"type\":\"rate_limit\"}}"));
            server.enqueue(new MockResponse().setBody("{\"text\":\"第二次成功\"}"));
            try (YoWhisperSpeech speech = YoWhisperSpeech.builder()
                    .maxRetries(1)
                    .initialRetryDelayMillis(0)
                    .build()) {
                assertEquals("第二次成功", speech.transcribe(
                        server.url("/").toString(), API_KEY, request()).text());
            }
            assertEquals(2, server.getRequestCount());
        }
    }

    // ---------------------------------------------------------------- 辅助

    private static YoWhisperSpeech speech(FakeTransport transport, int maxRetries) {
        return new YoWhisperSpeech(transport, maxRetries, 0L, 0L);
    }

    private static YoTranscriptionRequest request() {
        return YoTranscriptionRequest.wav(new byte[]{1, 2, 3, 4}, "whisper-1");
    }

    private static SpeechTransport.Response ok(String body) {
        return new SpeechTransport.Response(200, "req_ok", body, null);
    }

    private static YoAiException failure(YoWhisperSpeech speech) {
        try {
            speech.transcribe(BASE_URL, API_KEY, request());
        } catch (YoAiException expected) {
            return expected;
        }
        throw new AssertionError("expected the request to fail");
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** 假传输：按队列吐出预设答复（状态码或异常），并记下每次调用。 */
    private static final class FakeTransport implements SpeechTransport {

        private final ArrayDeque<Object> replies = new ArrayDeque<>();
        final List<RecordedCall> calls = new ArrayList<>();
        boolean closed;

        FakeTransport reply(SpeechTransport.Response response) {
            replies.add(response == null ? new AssertionError("null reply") : response);
            return this;
        }

        FakeTransport fail(RuntimeException error) {
            replies.add(error);
            return this;
        }

        RecordedCall onlyCall() {
            assertEquals("应当只发一次请求", 1, calls.size());
            return calls.get(0);
        }

        @Override
        public SpeechTransport.Response post(
                String url, Map<String, String> headers, byte[] body, String contentType) {
            calls.add(new RecordedCall(url, Map.copyOf(headers), body.clone(), contentType));
            Object reply = replies.poll();
            if (reply == null) {
                throw new AssertionError("unexpected extra request to " + url);
            }
            if (reply instanceof RuntimeException error) {
                throw error;
            }
            return (SpeechTransport.Response) reply;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** 记下的一次调用。 */
    private static final class RecordedCall {

        final String url;
        final Map<String, String> headers;
        final byte[] body;
        final String contentType;

        RecordedCall(String url, Map<String, String> headers, byte[] body, String contentType) {
            this.url = url;
            this.headers = headers;
            this.body = body;
            this.contentType = contentType;
        }
    }
}
