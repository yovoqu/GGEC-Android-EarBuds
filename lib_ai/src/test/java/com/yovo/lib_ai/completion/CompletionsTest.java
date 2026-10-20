package com.yovo.lib_ai.completion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.platform.anthropic.AnthropicPlatform;
import com.yovo.lib_ai.platform.deepseek.DeepSeekPlatform;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;
import com.yovo.lib_ai.platform.sensenova.SenseNovaPlatform;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class CompletionsTest {

    @Test
    public void chatStreamsReasoningThenTextAndSummarisesUsage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(sse("""
                    data: {"id":"c1","model":"deepseek-reasoner","choices":[{"index":0,"delta":{"role":"assistant","reasoning_content":"先"}}]}

                    data: {"id":"c1","choices":[{"index":0,"delta":{"reasoning_content":"想想"}}]}

                    data: {"id":"c1","choices":[{"index":0,"delta":{"content":"你"}}]}

                    data: {"id":"c1","choices":[{"index":0,"delta":{"content":"好"},"finish_reason":"length"}]}

                    data: {"id":"c1","choices":[],"usage":{"prompt_tokens":7,"completion_tokens":3,"total_tokens":10}}

                    data: [DONE]

                    """));
            try (YoAiClient client = client(DeepSeekPlatform.builder().baseUrl(baseUrl(server)).apiKey("k").build())) {
                Capture capture = new Capture();
                client.completions().stream(hello(), capture);
                capture.await();

                assertEquals(List.of("REASONING:先", "REASONING:想想", "TEXT:你", "TEXT:好"), capture.events);
                YoCompletionResult result = capture.result;
                assertEquals("你好", result.text());
                assertEquals("先想想", result.reasoning());
                assertEquals(YoFinishReason.LENGTH, result.finishReason());
                assertEquals(YoProtocol.CHAT_COMPLETIONS, result.protocol());
                assertEquals(7, result.usage().inputTokens());
                assertEquals(3, result.usage().outputTokens());
                assertEquals(10, result.usage().totalTokens());
                assertEquals("deepseek-reasoner", result.model());
                assertEquals("/v1/chat/completions", server.takeRequest().getPath());
            }
        }
    }

    @Test
    public void chatRequestCarriesSystemPromptHistoryAndSamplingOptions() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("""
                    {"id":"c2","model":"gpt-4o","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"ok","reasoning_content":"because"}}],"usage":{"prompt_tokens":5,"completion_tokens":1,"total_tokens":6}}
                    """));
            try (YoAiClient client = client(OpenAiPlatform.builder().baseUrl(baseUrl(server)).apiKey("k").build())) {
                YoCompletionResult result = client.completions().complete(YoCompletionRequest.builder()
                        .model("gpt-4o")
                        .system("be brief")
                        .addTurn(YoCompletionTurn.user("hi"))
                        .addTurn(YoCompletionTurn.assistant("hello"))
                        .addTurn(YoCompletionTurn.user("again"))
                        .temperature(0.3)
                        .topP(0.9)
                        .maxOutputTokens(64)
                        .build());

                assertEquals("ok", result.text());
                assertEquals("because", result.reasoning());
                assertEquals(YoFinishReason.STOP, result.finishReason());
                JSONObject body = new JSONObject(server.takeRequest().getBody().readUtf8());
                JSONArray messages = body.getJSONArray("messages");
                assertEquals(4, messages.length());
                assertEquals("system", messages.getJSONObject(0).getString("role"));
                assertEquals("be brief", messages.getJSONObject(0).getString("content"));
                assertEquals("assistant", messages.getJSONObject(2).getString("role"));
                assertEquals("again", messages.getJSONObject(3).getString("content"));
                assertEquals(0.3, body.getDouble("temperature"), 0.0001);
                assertEquals(0.9, body.getDouble("top_p"), 0.0001);
                assertEquals(64, body.getInt("max_completion_tokens"));
                assertFalse(body.has("stream"));
            }
        }
    }

    @Test
    public void messagesStreamSeparatesThinkingFromText() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(sse("""
                    event: message_start
                    data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"model":"claude-x","usage":{"input_tokens":9,"output_tokens":1}}}

                    event: content_block_start
                    data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}

                    event: content_block_stop
                    data: {"type":"content_block_stop","index":0}

                    event: content_block_start
                    data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Hi"}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"!"}}

                    event: message_delta
                    data: {"type":"message_delta","delta":{"stop_reason":"max_tokens","stop_sequence":null},"usage":{"output_tokens":5}}

                    event: message_stop
                    data: {"type":"message_stop"}

                    """));
            try (YoAiClient client = client(AnthropicPlatform.builder().baseUrl(baseUrl(server)).apiKey("k").build())) {
                Capture capture = new Capture();
                client.completions().stream(YoCompletionRequest.builder()
                        .model("claude-x")
                        .system("be brief")
                        .addTurn(YoCompletionTurn.user("hi"))
                        .temperature(0.2)
                        .topP(0.8)
                        .build(), capture);
                capture.await();

                assertEquals(List.of("REASONING:hmm", "TEXT:Hi", "TEXT:!"), capture.events);
                YoCompletionResult result = capture.result;
                assertEquals("Hi!", result.text());
                assertEquals("hmm", result.reasoning());
                assertEquals(YoFinishReason.LENGTH, result.finishReason());
                assertEquals(YoProtocol.MESSAGES, result.protocol());
                assertEquals(9, result.usage().inputTokens());
                assertEquals(5, result.usage().outputTokens());
                assertEquals(14, result.usage().totalTokens());

                JSONObject body = new JSONObject(server.takeRequest().getBody().readUtf8());
                assertEquals(4096, body.getInt("max_tokens"));
                assertEquals("be brief", body.getString("system"));
                assertEquals(0.2, body.getDouble("temperature"), 0.0001);
                assertFalse("temperature and top_p must not be sent together", body.has("top_p"));
            }
        }
    }

    @Test
    public void messagesRequestHonoursExplicitMaxTokensAndTopP() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("""
                    {"id":"msg_2","type":"message","role":"assistant","model":"claude-x","stop_reason":"end_turn","content":[{"type":"text","text":"Hi"}],"usage":{"input_tokens":1,"output_tokens":1}}
                    """));
            try (YoAiClient client = client(AnthropicPlatform.builder().baseUrl(baseUrl(server)).apiKey("k").build())) {
                YoCompletionResult result = client.completions().complete(YoCompletionRequest.builder()
                        .model("claude-x")
                        .addTurn(YoCompletionTurn.user("hi"))
                        .topP(0.8)
                        .maxOutputTokens(128)
                        .build());
                assertEquals("Hi", result.text());
                assertNull(result.reasoning());
                assertEquals(YoFinishReason.STOP, result.finishReason());
                JSONObject body = new JSONObject(server.takeRequest().getBody().readUtf8());
                assertEquals(128, body.getInt("max_tokens"));
                assertEquals(0.8, body.getDouble("top_p"), 0.0001);
                assertFalse(body.has("temperature"));
                assertFalse(body.has("system"));
            }
        }
    }

    @Test
    public void responsesStreamSeparatesReasoningAndEncodesAssistantHistoryAsOutputText() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(sse("""
                    event: response.created
                    data: {"type":"response.created","response":{"id":"resp_1","status":"in_progress","model":"gpt-x","output":[]}}

                    event: response.reasoning_summary_text.delta
                    data: {"type":"response.reasoning_summary_text.delta","output_index":0,"summary_index":0,"delta":"plan"}

                    event: response.output_text.delta
                    data: {"type":"response.output_text.delta","output_index":1,"content_index":0,"delta":"Yo"}

                    event: response.output_text.delta
                    data: {"type":"response.output_text.delta","output_index":1,"content_index":0,"delta":"!"}

                    event: response.completed
                    data: {"type":"response.completed","response":{"id":"resp_1","status":"completed","model":"gpt-x","output":[{"type":"reasoning","id":"rs_1","summary":[{"type":"summary_text","text":"plan"}]},{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Yo!"}]}],"usage":{"input_tokens":4,"output_tokens":2,"total_tokens":6}}}

                    """));
            try (YoAiClient client = client(OpenAiPlatform.builder().baseUrl(baseUrl(server)).apiKey("k").build())) {
                Capture capture = new Capture();
                client.completions().stream(YoCompletionRequest.builder()
                        .model("gpt-x")
                        .protocol(YoProtocol.RESPONSES)
                        .system("be brief")
                        .addTurn(YoCompletionTurn.user("hi"))
                        .addTurn(YoCompletionTurn.assistant("hello"))
                        .addTurn(YoCompletionTurn.user("again"))
                        .maxOutputTokens(50)
                        .build(), capture);
                capture.await();

                assertEquals(List.of("REASONING:plan", "TEXT:Yo", "TEXT:!"), capture.events);
                assertEquals("Yo!", capture.result.text());
                assertEquals("plan", capture.result.reasoning());
                assertEquals(YoProtocol.RESPONSES, capture.result.protocol());
                assertEquals(6, capture.result.usage().totalTokens());
                assertEquals("resp_1", capture.result.responseId());

                RecordedRequest recorded = server.takeRequest();
                assertEquals("/v1/responses", recorded.getPath());
                JSONObject body = new JSONObject(recorded.getBody().readUtf8());
                assertEquals("be brief", body.getString("instructions"));
                assertEquals(50, body.getInt("max_output_tokens"));
                JSONArray input = body.getJSONArray("input");
                assertEquals("input_text", input.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("type"));
                assertEquals("assistant", input.getJSONObject(1).getString("role"));
                assertEquals("output_text", input.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("type"));
            }
        }
    }

    @Test
    public void incompleteResponsesMapToTheLengthReason() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("""
                    {"id":"resp_9","status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"cut"}]}]}
                    """));
            try (YoAiClient client = client(OpenAiPlatform.builder().baseUrl(baseUrl(server)).apiKey("k").build())) {
                YoCompletionResult result = client.completions().complete(YoCompletionRequest.builder()
                        .model("gpt-x")
                        .protocol(YoProtocol.RESPONSES)
                        .addTurn(YoCompletionTurn.user("hi"))
                        .build());
                assertEquals("cut", result.text());
                assertEquals(YoFinishReason.LENGTH, result.finishReason());
            }
        }
    }

    @Test
    public void protocolsFollowThePlatformAndDefaultsPreferChat() {
        try (YoAiClient client = YoAiClient.builder()
                .platform(AnthropicPlatform.builder().apiKey("k").build())
                .platform(SenseNovaPlatform.builder().apiKey("k").build())
                .platform(OpenAiPlatform.builder().id("openai-b").apiKey("k").build())
                .build()) {
            assertEquals(Set.of(YoProtocol.MESSAGES), client.completions("anthropic").protocols());
            assertEquals(YoProtocol.MESSAGES, client.completions("anthropic").defaultProtocol());
            assertEquals(Set.of(YoProtocol.CHAT_COMPLETIONS, YoProtocol.RESPONSES, YoProtocol.MESSAGES),
                    client.completions("sensenova").protocols());
            assertEquals(YoProtocol.CHAT_COMPLETIONS, client.completions("sensenova").defaultProtocol());
            assertEquals(YoProtocol.CHAT_COMPLETIONS, client.completions("openai-b").defaultProtocol());
        }
    }

    @Test
    public void unavailableProtocolIsRejectedBeforeAnyRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            try (YoAiClient client = client(AnthropicPlatform.builder().baseUrl(baseUrl(server)).apiKey("k").build())) {
                try {
                    client.completions().complete(YoCompletionRequest.builder()
                            .model("claude-x")
                            .protocol(YoProtocol.CHAT_COMPLETIONS)
                            .addTurn(YoCompletionTurn.user("hi"))
                            .build());
                    fail("anthropic has no chat completions");
                } catch (IllegalArgumentException expected) {
                    assertTrue(expected.getMessage().contains("chat_completions"));
                }
                assertEquals(0, server.getRequestCount());
            }
        }
    }

    @Test
    public void streamFailuresReachTheListener() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(401).setBody("""
                    {"error":{"message":"bad key","type":"authentication_error"}}
                    """));
            try (YoAiClient client = client(OpenAiPlatform.builder().baseUrl(baseUrl(server)).apiKey("bad").build())) {
                Capture capture = new Capture();
                client.completions().stream(hello(), capture);
                capture.await();
                assertNull(capture.result);
                assertTrue(capture.error instanceof YoAiHttpException);
                assertEquals(401, ((YoAiHttpException) capture.error).statusCode());
            }
        }
    }

    @Test
    public void requestValidationRejectsUnusableConversations() {
        assertRejected(() -> YoCompletionRequest.builder().model("m").build());
        assertRejected(() -> YoCompletionRequest.builder().model("m")
                .addTurn(YoCompletionTurn.user("a")).addTurn(YoCompletionTurn.assistant("b")).build());
        assertRejected(() -> YoCompletionRequest.builder().addTurn(YoCompletionTurn.user("a")).build());
        assertRejected(() -> YoCompletionTurn.user("  "));
        assertRejected(() -> YoCompletionRequest.builder().temperature(2.5));
        assertRejected(() -> YoCompletionRequest.builder().topP(0.0));
        assertRejected(() -> YoCompletionRequest.builder().maxOutputTokens(0));
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static YoCompletionRequest hello() {
        return YoCompletionRequest.builder().model("m").addTurn(YoCompletionTurn.user("hello")).build();
    }

    private static MockResponse sse(String body) {
        return new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body);
    }

    private static YoAiClient client(YoPlatform platform) {
        return YoAiClient.builder()
                .platform(platform)
                .maxRetries(0)
                .callbackExecutor(Runnable::run)
                .build();
    }

    private static String baseUrl(MockWebServer server) {
        return server.url("/").toString().replaceAll("/+$", "");
    }

    private static final class Capture implements YoCompletionListener {
        final List<String> events = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        volatile YoCompletionResult result;
        volatile YoAiException error;

        @Override
        public void onEvent(YoCompletionEvent event) {
            events.add(event.type() + ":" + event.delta());
        }

        @Override
        public void onComplete(YoCompletionResult completion) {
            result = completion;
            done.countDown();
        }

        @Override
        public void onFailure(YoAiException failure) {
            error = failure;
            done.countDown();
        }

        void await() throws InterruptedException {
            assertTrue("stream did not finish", done.await(5, TimeUnit.SECONDS));
        }
    }
}
