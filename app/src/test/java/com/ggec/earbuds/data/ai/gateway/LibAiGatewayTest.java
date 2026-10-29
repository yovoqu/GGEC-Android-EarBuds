package com.ggec.earbuds.data.ai.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiGenerationDefaults;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiRole;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiChatListener;
import com.ggec.earbuds.domain.ai.port.AiChatRequest;
import com.ggec.earbuds.domain.ai.port.AiChatResult;
import com.ggec.earbuds.domain.ai.port.AiChatTurn;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;
import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.completion.YoCompletionRequest;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class LibAiGatewayTest {

    private static final String KEY = "sk-live-1234567890abcdef";

    private MockWebServer server;
    private LibAiClientPool pool;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        pool = new LibAiClientPool(Runnable::run);
    }

    @After
    public void tearDown() throws Exception {
        server.close();
    }

    @Test
    public void fetchKeepsRealtimeModelsForVoiceMode() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"object":"list","data":[
                  {"id":"gpt-4o","created":1715367049},
                  {"id":"gpt-4o-realtime-preview","created":1715367049}
                ]}
                """));

        List<AiModel> models = new LibAiModelGateway(pool).fetch(config(AiProtocol.CHAT_COMPLETIONS));

        assertEquals(List.of("gpt-4o", "gpt-4o-realtime-preview"),
                models.stream().map(AiModel::id).toList());
        assertTrue("实时模型不能被当成音频模型丢掉",
                models.get(1).features().contains(AiModelFeature.REALTIME_AUDIO));
    }

    @Test
    public void fetchKeepsChatModelsNewestFirstAndMapsMetadata() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"object":"list","data":[
                  {"id":"text-embedding-3-small","created":1705948997},
                  {"id":"gpt-old","created":1600000000},
                  {"id":"gpt-new","created":1715367049,"context_length":128000,"input_modalities":["text","image"]},
                  {"id":"no-date-b"},{"id":"no-date-a"},
                  {"id":"whisper-1","created":1677532384},
                  {"id":"o3-mini","created":1700000000}
                ]}
                """));

        List<AiModel> models = new LibAiModelGateway(pool).fetch(config(AiProtocol.CHAT_COMPLETIONS));

        assertEquals(List.of("gpt-new", "o3-mini", "gpt-old", "no-date-a", "no-date-b"),
                models.stream().map(AiModel::id).toList());
        assertEquals(Integer.valueOf(128_000), models.get(0).contextWindow());
        assertTrue(models.get(0).features().contains(AiModelFeature.VISION));
        assertTrue(models.get(1).reasoning());
        assertTrue(models.stream().noneMatch(AiModel::manual));
        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/models", request.getPath());
        assertEquals("Bearer " + KEY, request.getHeader("Authorization"));
        assertEquals("every path hands the client lease back", 0, pool.leasesInUse());
    }

    @Test
    public void fetchFailuresBecomeDomainFailuresWithoutLeakingTheKey() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody(
                "{\"error\":{\"message\":\"Incorrect API key provided: " + KEY + ". Check it.\","
                        + "\"type\":\"invalid_request_error\"}}"));

        try {
            new LibAiModelGateway(pool).fetch(config(AiProtocol.CHAT_COMPLETIONS));
            fail("a rejected key must surface");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.AUTHENTICATION, failure.kind());
            assertFalse(failure.getMessage().contains(KEY));
            assertTrue(failure.getMessage().contains("Incorrect API key"));
        }
        assertEquals(0, pool.leasesInUse());
    }

    @Test
    public void unreachableHostsAreNetworkFailures() throws Exception {
        String dead = server.url("/").toString().replaceAll("/+$", "");
        server.close();

        try {
            new LibAiModelGateway(pool).fetch(config("custom-dead", dead, AiProtocol.CHAT_COMPLETIONS));
            fail("nothing is listening");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.NETWORK, failure.kind());
        }
        assertEquals(0, pool.leasesInUse());
    }

    @Test
    public void chatStreamsReasoningAndTextThenReleasesTheLease() throws Exception {
        server.enqueue(sse("""
                data: {"id":"c1","model":"m","choices":[{"index":0,"delta":{"reasoning_content":"想"}}]}

                data: {"id":"c1","choices":[{"index":0,"delta":{"content":"你"}}]}

                data: {"id":"c1","choices":[{"index":0,"delta":{"content":"好"},"finish_reason":"stop"}]}

                data: {"id":"c1","choices":[],"usage":{"prompt_tokens":4,"completion_tokens":2,"total_tokens":6}}

                data: [DONE]

                """));
        Recorder recorder = new Recorder();

        new LibAiChatGateway(pool).stream(chatRequest(AiProtocol.CHAT_COMPLETIONS), recorder);
        recorder.await();

        assertEquals(List.of("reasoning:想", "text:你", "text:好", "complete:你好:想:STOP:4/2"), recorder.log);
        JSONObject body = new JSONObject(server.takeRequest().getBody().readUtf8());
        assertEquals("gpt-test", body.getString("model"));
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("assistant", body.getJSONArray("messages").getJSONObject(2).getString("role"));
        assertEquals(0, pool.leasesInUse());
    }

    @Test
    public void chatFailuresMapToDomainKinds() {
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0")
                    .setBody("{\"error\":{\"message\":\"slow down\",\"type\":\"rate_limit_error\"}}"));
        }
        Recorder recorder = new Recorder();

        new LibAiChatGateway(pool).stream(chatRequest(AiProtocol.CHAT_COMPLETIONS), recorder);
        recorder.await();

        assertEquals(List.of("failure:RATE_LIMITED:slow down"), recorder.log);
        assertEquals(0, pool.leasesInUse());
    }

    @Test
    public void cancellingAStreamStopsCallbacksAndReleasesTheLease() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBodyDelay(3, TimeUnit.SECONDS)
                .setBody("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"late\"}}]}\n\ndata: [DONE]\n\n"));
        Recorder recorder = new Recorder();

        AiCancellable handle = new LibAiChatGateway(pool).stream(chatRequest(AiProtocol.CHAT_COMPLETIONS), recorder);
        assertEquals(1, pool.leasesInUse());
        handle.cancel();
        handle.cancel();

        assertEquals(0, pool.leasesInUse());
        Thread.sleep(200);
        assertTrue(recorder.log.isEmpty());
    }

    @Test
    public void aRequestTheLibraryRejectsFailsSynchronouslyAndReleasesTheLease() {
        try {
            new LibAiChatGateway(pool).stream(chatRequest(AiProtocol.MESSAGES), new Recorder());
            fail("an OpenAI compatible platform has no messages protocol");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.BAD_REQUEST, failure.kind());
        }
        assertEquals(0, pool.leasesInUse());
    }

    @Test
    public void theAppDefaultsReachTheLibraryRequest() {
        YoCompletionRequest lib = LibAiChatGateway.toRequest(chatRequest(AiProtocol.CHAT_COMPLETIONS));

        assertEquals(AiGenerationDefaults.TEMPERATURE, lib.temperature(), 1e-9);
        assertEquals(AiGenerationDefaults.TOP_P, lib.topP(), 1e-9);
        assertEquals(Integer.valueOf(AiGenerationDefaults.MAX_OUTPUT_TOKENS), lib.maxOutputTokens());
    }

    @Test
    public void speedTestMapsResultsAndReleasesTheLeaseWhenDone() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                try {
                    String model = new JSONObject(request.getBody().readUtf8()).getString("model");
                    if (model.equals("missing")) {
                        return new MockResponse().setResponseCode(404)
                                .setBody("{\"error\":{\"message\":\"no such model\"}}");
                    }
                    return sse("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"pong\"},"
                            + "\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n");
                } catch (Exception failure) {
                    return new MockResponse().setResponseCode(400);
                }
            }
        });
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        Map<String, AiSpeedResult> results = new java.util.concurrent.ConcurrentHashMap<>();
        CountDownLatch done = new CountDownLatch(1);

        new LibAiSpeedTestGateway(pool).run(config(AiProtocol.CHAT_COMPLETIONS), List.of("fast", "missing"),
                new AiSpeedTestListener() {
                    @Override
                    public void onStarted(String modelId) {
                        log.add("start:" + modelId);
                    }

                    @Override
                    public void onResult(AiSpeedResult result) {
                        results.put(result.modelId(), result);
                    }

                    @Override
                    public void onComplete(boolean cancelled) {
                        log.add("done:" + cancelled);
                        done.countDown();
                    }
                });

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(log.containsAll(List.of("start:fast", "start:missing", "done:false")));
        assertEquals(AiSpeedResult.Status.OK, results.get("fast").status());
        assertTrue(results.get("fast").firstTokenMillis() >= 0);
        assertEquals(AiSpeedResult.Status.FAILED, results.get("missing").status());
        assertEquals(AiFailureKind.NOT_FOUND, results.get("missing").failureKind());
        assertEquals("no such model", results.get("missing").message());
        assertEquals(0L, results.get("fast").testedAtMillis());
        assertEquals("the lease goes back when the probe completes", 0, pool.leasesInUse());
    }

    @Test
    public void speedTestRejectedUpFrontReleasesTheLease() {
        try {
            new LibAiSpeedTestGateway(pool).run(config(AiProtocol.MESSAGES), List.of("m"),
                    done -> { });
            fail("an OpenAI compatible platform has no messages protocol");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.BAD_REQUEST, failure.kind());
        }
        assertEquals(0, pool.leasesInUse());
    }

    @Test
    public void poolReusesAClientUntilTheConnectionDetailsChange() {
        AiPlatformConfig original = config(AiProtocol.CHAT_COMPLETIONS);
        LibAiClientPool.Lease first = pool.acquire(original);
        LibAiClientPool.Lease second = pool.acquire(original);
        assertSame(first.client(), second.client());
        assertEquals(2, pool.leasesInUse());

        AiPlatformConfig newKey = original.withCredentials(original.name(), "sk-rotated-key-9999",
                original.baseUrl(), original.protocol());
        LibAiClientPool.Lease third = pool.acquire(newKey);
        assertNotSame(first.client(), third.client());

        YoAiClient stale = first.client();
        first.close();
        first.close();
        assertEquals("a retired client stays open while someone still holds it", 2, pool.leasesInUse());
        assertTrue(canStillBeUsed(stale));
        second.close();
        assertFalse("the retired client closes with its last lease", canStillBeUsed(stale));
        third.close();
        assertEquals(0, pool.leasesInUse());
    }

    @Test
    public void protocolChangesDoNotRebuildTheClient() {
        AiPlatformConfig chat = config(AiProtocol.CHAT_COMPLETIONS);
        LibAiClientPool.Lease a = pool.acquire(chat);
        LibAiClientPool.Lease b = pool.acquire(config(AiProtocol.RESPONSES));

        assertSame(a.client(), b.client());
        a.close();
        b.close();
    }

    @Test
    public void catalogExposesTheLibraryPlatformsAsTemplates() {
        LibAiPlatformCatalog catalog = new LibAiPlatformCatalog();

        assertEquals(List.of("openai", "anthropic", "deepseek", "sensenova", "openai-compatible", "anthropic-compatible"),
                catalog.templates().stream().map(t -> t.id()).toList());
        assertEquals(AiProtocol.MESSAGES, catalog.find("anthropic").orElseThrow().defaultProtocol());
        assertTrue(catalog.find("openai-compatible").orElseThrow().custom());
        assertTrue(catalog.find("nobody").isEmpty());
        assertEquals("https://x.example.com", catalog.normalizeBaseUrl("https://x.example.com/v1/"));
    }

    @Test
    public void failureRedactionCoversTheKeyAndTokenLookalikes() {
        assertEquals("bad *** here", LibAiFailures.redact("bad " + KEY + " here", KEY));
        assertEquals("seen sk-*** only", LibAiFailures.redact("seen sk-abcdefghijklmnop only", null));
        assertEquals("", LibAiFailures.redact(null, KEY));
        assertEquals(300, LibAiFailures.redact("x".repeat(500), null).length());
        assertEquals("short keys are not worth masking", "key abc here",
                LibAiFailures.redact("key abc here", "abc"));
    }

    private boolean canStillBeUsed(YoAiClient client) {
        try {
            client.chatCompletions();
            return true;
        } catch (IllegalStateException closed) {
            return false;
        }
    }

    private AiPlatformConfig config(AiProtocol protocol) {
        return config("custom-test", server.url("/").toString().replaceAll("/+$", ""), protocol);
    }

    private static AiPlatformConfig config(String id, String baseUrl, AiProtocol protocol) {
        return new AiPlatformConfig(id, "openai-compatible", "测试平台", baseUrl, KEY, protocol,
                List.of(new AiModel("gpt-test", "gpt-test", null, null, java.util.Set.of(), false)), 0L, Map.of());
    }

    private AiChatRequest chatRequest(AiProtocol protocol) {
        return new AiChatRequest(config(protocol), "gpt-test", "be brief",
                List.of(new AiChatTurn(AiRole.USER, "hi"), new AiChatTurn(AiRole.ASSISTANT, "hello"),
                        new AiChatTurn(AiRole.USER, "again")));
    }

    private static MockResponse sse(String body) {
        return new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body);
    }

    private static final class Recorder implements AiChatListener {
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void onReasoning(String delta) {
            log.add("reasoning:" + delta);
        }

        @Override
        public void onText(String delta) {
            log.add("text:" + delta);
        }

        @Override
        public void onComplete(AiChatResult result) {
            AiFinishReason finish = result.finishReason();
            log.add("complete:" + result.text() + ":" + result.reasoning() + ":" + finish + ":"
                    + result.usage().inputTokens() + "/" + result.usage().outputTokens());
            done.countDown();
        }

        @Override
        public void onFailure(AiFailure error) {
            log.add("failure:" + error.kind() + ":" + error.getMessage());
            done.countDown();
        }

        void await() {
            try {
                assertTrue("stream did not finish", done.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("interrupted");
            }
        }
    }
}
