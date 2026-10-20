package com.yovo.lib_ai.probe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.platform.anthropic.AnthropicPlatform;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

public final class LatencyProbeTest {

    @Test
    public void ranksModelsByFirstTokenLatency() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(byModel(model -> switch (model) {
                case "slow" -> chat("slow", 1_200);
                case "mid" -> chat("mid", 600);
                default -> chat(model, 0);
            }));
            server.start();
            try (YoAiClient client = client(server)) {
                Collector collector = new Collector();
                client.latencyProbe().run(YoProbePlan.builder()
                        .models(List.of("slow", "fast", "mid"))
                        .concurrency(3)
                        .build(), collector);
                collector.await();

                YoProbeSummary summary = collector.summary;
                assertFalse(summary.cancelled());
                assertEquals(3, summary.results().size());
                assertEquals(List.of("fast", "mid", "slow"),
                        summary.ranked().stream().map(YoProbeResult::model).toList());
                assertEquals("fast", summary.fastest().model());
                YoProbeResult slow = collector.result("slow");
                assertTrue(slow.ok());
                assertTrue("first token waited for the body: " + slow.firstTokenMillis(), slow.firstTokenMillis() >= 1_100);
                assertTrue(slow.totalMillis() >= slow.firstTokenMillis());
                assertEquals("pong", slow.sample());
                assertTrue(collector.started.containsAll(List.of("slow", "fast", "mid")));
            }
        }
    }

    @Test
    public void reportsFailuresPerModelAndNeverRetries() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(byModel(model -> switch (model) {
                case "missing" -> new MockResponse().setResponseCode(404).setBody("""
                        {"error":{"message":"The model does not exist","type":"invalid_request_error"}}
                        """);
                case "boom" -> new MockResponse().setResponseCode(500).setBody("upstream exploded");
                default -> chat(model, 0);
            }));
            server.start();
            try (YoAiClient client = client(server)) {
                Collector collector = new Collector();
                client.latencyProbe().run(YoProbePlan.builder()
                        .models(List.of("boom", "missing", "ok"))
                        .build(), collector);
                collector.await();

                assertEquals("a probe must not retry or the backoff pollutes the timing", 3, server.getRequestCount());
                YoProbeResult missing = collector.result("missing");
                assertEquals(YoProbeResult.Status.FAILED, missing.status());
                assertEquals(YoAiErrorKind.NOT_FOUND, missing.errorKind());
                assertEquals(404, missing.httpStatus());
                assertEquals("The model does not exist", missing.message());
                assertEquals(-1L, missing.firstTokenMillis());
                YoProbeResult boom = collector.result("boom");
                assertEquals(YoAiErrorKind.SERVER, boom.errorKind());
                assertEquals(500, boom.httpStatus());
                assertEquals("ok", collector.summary.fastest().model());
                assertEquals("ok", collector.summary.ranked().get(0).model());
            }
        }
    }

    @Test
    public void timeoutAbandonsAStalledModelWithoutBlockingTheRest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(byModel(model -> "stuck".equals(model)
                    ? new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                    : chat(model, 0)));
            server.start();
            try (YoAiClient client = client(server)) {
                Collector collector = new Collector();
                long started = System.nanoTime();
                client.latencyProbe().run(YoProbePlan.builder()
                        .models(List.of("stuck", "ok"))
                        .concurrency(2)
                        .timeoutMillis(1_200)
                        .build(), collector);
                collector.await();

                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 4_000);
                YoProbeResult stuck = collector.result("stuck");
                assertEquals(YoProbeResult.Status.TIMEOUT, stuck.status());
                assertEquals(YoAiErrorKind.TIMEOUT, stuck.errorKind());
                assertEquals(1_200L, stuck.totalMillis());
                assertTrue(collector.result("ok").ok());
                assertEquals("ok", collector.summary.fastest().model());
            }
        }
    }

    @Test
    public void cancelDropsQueuedModelsAndStillCompletesOnce() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(byModel(model -> chat(model, 250)));
            server.start();
            try (YoAiClient client = client(server)) {
                AtomicReference<YoAiCall> call = new AtomicReference<>();
                Collector collector = new Collector() {
                    @Override
                    public void onResult(YoProbeResult result) {
                        super.onResult(result);
                        call.get().cancel();
                    }
                };
                call.set(client.latencyProbe().run(YoProbePlan.builder()
                        .models(List.of("m1", "m2", "m3", "m4", "m5", "m6"))
                        .concurrency(1)
                        .build(), collector));
                collector.await();

                assertTrue(collector.summary.cancelled());
                assertTrue("queued models must be dropped", collector.summary.results().size() < 6);
                assertTrue(server.getRequestCount() < 6);
                assertEquals(1, collector.completions);
            }
        }
    }

    @Test
    public void rejectsAProtocolThePlatformDoesNotProvide() {
        try (YoAiClient client = YoAiClient.builder()
                .platform(AnthropicPlatform.builder().apiKey("k").build())
                .build()) {
            try {
                client.latencyProbe().run(YoProbePlan.builder()
                        .protocol(YoProtocol.CHAT_COMPLETIONS)
                        .addModel("claude-x")
                        .build(), summary -> { });
                fail("anthropic has no chat completions");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("chat_completions"));
            }
        }
    }

    @Test
    public void planValidationRejectsUnusableInput() {
        assertRejected(() -> YoProbePlan.builder().build());
        assertRejected(() -> YoProbePlan.builder().addModel(" "));
        assertRejected(() -> YoProbePlan.builder().concurrency(0));
        assertRejected(() -> YoProbePlan.builder().concurrency(YoProbePlan.MAX_CONCURRENCY + 1));
        assertRejected(() -> YoProbePlan.builder().timeoutMillis(10));
        assertRejected(() -> YoProbePlan.builder().prompt(" "));
        assertRejected(() -> YoProbePlan.builder().maxOutputTokens(0));
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= YoProbePlan.MAX_MODELS; i++) {
            tooMany.add("m" + i);
        }
        assertRejected(() -> YoProbePlan.builder().models(tooMany).build());
        YoProbePlan plan = YoProbePlan.builder().models(List.of("a", "a", " b ")).build();
        assertEquals(List.of("a", "b"), plan.models());
        assertEquals(3, plan.concurrency());
    }

    @Test
    public void summaryRanksSuccessesFirstAndHasNoFastestWhenAllFailed() {
        YoProbeSummary mixed = new YoProbeSummary(List.of(
                YoProbeResult.failed("bad", 30, YoAiErrorKind.NETWORK, "x", 0),
                YoProbeResult.ok("b", 200, 500, null),
                YoProbeResult.ok("a", 200, 400, null),
                YoProbeResult.timeout("slow", 5_000)), false);
        assertEquals(List.of("a", "b", "bad", "slow"),
                mixed.ranked().stream().map(YoProbeResult::model).toList());
        assertEquals("a", mixed.fastest().model());

        YoProbeSummary failed = new YoProbeSummary(
                List.of(YoProbeResult.failed("bad", 30, YoAiErrorKind.NETWORK, "x", 0)), false);
        assertNull(failed.fastest());
        assertNull(new YoProbeSummary(Collections.emptyList(), true).fastest());
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 期望的拒绝。
        }
    }

    private static MockResponse chat(String model, long bodyDelayMillis) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBodyDelay(bodyDelayMillis, TimeUnit.MILLISECONDS)
                .setBody("data: {\"id\":\"c\",\"model\":\"" + model
                        + "\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"pong\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n");
    }

    private static Dispatcher byModel(Function<String, MockResponse> responses) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                try {
                    String model = new JSONObject(request.getBody().readUtf8()).getString("model");
                    return responses.apply(model);
                } catch (Exception exception) {
                    return new MockResponse().setResponseCode(400);
                }
            }
        };
    }

    private static YoAiClient client(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/+$", "");
        return YoAiClient.builder()
                .platform(OpenAiPlatform.builder().baseUrl(baseUrl).apiKey("k").build())
                .maxRetries(2)
                .initialRetryDelayMillis(0)
                .callbackExecutor(Runnable::run)
                .build();
    }

    private static class Collector implements YoProbeListener {
        final List<String> started = Collections.synchronizedList(new ArrayList<>());
        final Map<String, YoProbeResult> results = new ConcurrentHashMap<>();
        final CountDownLatch done = new CountDownLatch(1);
        volatile YoProbeSummary summary;
        volatile int completions;

        @Override
        public void onStarted(String model) {
            started.add(model);
        }

        @Override
        public void onResult(YoProbeResult result) {
            results.put(result.model(), result);
        }

        @Override
        public void onComplete(YoProbeSummary completed) {
            completions++;
            summary = completed;
            done.countDown();
        }

        YoProbeResult result(String model) {
            YoProbeResult result = results.get(model);
            assertTrue("no result for " + model, result != null);
            return result;
        }

        void await() throws InterruptedException {
            assertTrue("probe did not complete", done.await(15, TimeUnit.SECONDS));
        }
    }
}
