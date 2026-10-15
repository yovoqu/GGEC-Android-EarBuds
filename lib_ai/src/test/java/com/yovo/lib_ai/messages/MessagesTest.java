package com.yovo.lib_ai.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.endpoint.YoAnthropicEndpoint;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.internal.messages.MessageAccumulator;
import com.yovo.lib_ai.internal.messages.MessagesCodec;

import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class MessagesTest {

    @Test
    public void encodesAnthropicToolsAndSystem() throws Exception {
        YoMessageRequest request = YoMessageRequest.builder()
                .model("claude-sonnet")
                .maxTokens(128)
                .system("be brief")
                .addUserMessage("Hello")
                .addTool(YoMessageTool.function("get_weather", "lookup", new JSONObject().put("type", "object")))
                .toolChoice(YoMessageToolChoice.tool("get_weather"))
                .addStopSequence("END")
                .userId("user-1")
                .temperature(0.2)
                .build();
        JSONObject json = MessagesCodec.encode(request, false);
        assertEquals(128, json.getInt("max_tokens"));
        assertEquals("be brief", json.getString("system"));
        assertEquals("get_weather", json.getJSONArray("tools").getJSONObject(0).getString("name"));
        assertEquals("tool", json.getJSONObject("tool_choice").getString("type"));
        assertEquals("user-1", json.getJSONObject("metadata").getString("user_id"));
        assertFalse(json.has("stream"));
    }

    @Test
    public void accumulatesTextAndToolInput() throws Exception {
        MessageAccumulator accumulator = new MessageAccumulator();
        accumulator.apply(event("""
                {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"model":"claude","stop_reason":null,"usage":{"input_tokens":5,"output_tokens":1}}}
                """));
        accumulator.apply(event("""
                {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}
                """));
        accumulator.apply(event("""
                {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
                """));
        accumulator.apply(event("""
                {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"get_weather","input":{}}}
                """));
        accumulator.apply(event("""
                {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"city\\""}}
                """));
        accumulator.apply(event("""
                {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":":\\"SZ\\"}"}}
                """));
        accumulator.apply(event("""
                {"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":4}}
                """));
        accumulator.apply(event("{\"type\":\"message_stop\"}"));

        YoMessage message = accumulator.toMessage();
        assertEquals("Hello", message.text());
        assertEquals("tool_use", message.stopReason());
        assertEquals(5, message.usage().inputTokens());
        assertEquals(4, message.usage().outputTokens());
        YoMessageContent tool = message.content().get(1);
        assertEquals("get_weather", tool.name());
        assertEquals("SZ", tool.input().getString("city"));

        accumulator.apply(event("""
                {"type":"content_block_start","index":2,"content_block":{"type":"thinking","thinking":""}}
                """));
        accumulator.apply(event("""
                {"type":"content_block_delta","index":2,"delta":{"type":"thinking_delta","thinking":"hmm"}}
                """));
        accumulator.apply(event("""
                {"type":"content_block_delta","index":2,"delta":{"type":"signature_delta","signature":"sig_1"}}
                """));
        YoMessage replay = accumulator.toMessage();
        YoMessageContent thinking = replay.content().get(2);
        assertEquals("hmm", thinking.text());
        assertEquals("sig_1", thinking.signature());
    }

    @Test
    public void postsMessagesWithAnthropicHeaders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setHeader("request-id", "req_a").setBody("""
                    {"id":"msg_1","type":"message","role":"assistant","model":"claude","stop_reason":"end_turn","content":[{"type":"text","text":"Hi"}],"usage":{"input_tokens":1,"output_tokens":1}}
                    """));
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody("""
                    event: message_start
                    data: {"type":"message_start","message":{"id":"msg_2","type":"message","role":"assistant","content":[],"model":"claude","usage":{"input_tokens":2,"output_tokens":1}}}

                    event: content_block_start
                    data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                    event: ping
                    data: {"type":"ping"}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Yo"}}

                    event: content_block_stop
                    data: {"type":"content_block_stop","index":0}

                    event: message_delta
                    data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":2}}

                    event: message_stop
                    data: {"type":"message_stop"}

                    """));
            String baseUrl = server.url("/").toString().replaceAll("/+$", "");
            YoAiClient client = YoAiClient.builder()
                    .anthropicEndpoint(YoAnthropicEndpoint.builder()
                            .baseUrl(baseUrl)
                            .apiKey("test-key")
                            .addBeta("fine-grained-tool-streaming")
                            .build())
                    .initialRetryDelayMillis(0)
                    .callbackExecutor(Runnable::run)
                    .build();
            try {
                YoMessage message = client.messages().create(request());
                assertEquals("Hi", message.text());
                RecordedRequest recorded = server.takeRequest();
                assertEquals("/v1/messages", recorded.getPath());
                assertEquals("test-key", recorded.getHeader("x-api-key"));
                assertEquals("2023-06-01", recorded.getHeader("anthropic-version"));
                assertEquals("fine-grained-tool-streaming", recorded.getHeader("anthropic-beta"));
                assertTrue(recorded.getBody().readUtf8().contains("\"max_tokens\":32"));

                CountDownLatch done = new CountDownLatch(1);
                AtomicReference<String> text = new AtomicReference<>();
                client.messages().stream(request(), new YoMessageStreamListener() {
                    @Override
                    public void onEvent(YoMessageStreamEvent event) {
                    }

                    @Override
                    public void onComplete(YoMessage message) {
                        text.set(message.text());
                        done.countDown();
                    }

                    @Override
                    public void onFailure(YoAiException error) {
                        text.set(error.getMessage());
                        done.countDown();
                    }
                });
                assertTrue(done.await(5, TimeUnit.SECONDS));
                assertEquals("Yo", text.get());
            } finally {
                client.close();
            }
        }
    }

    private static JSONObject event(String json) throws Exception {
        return new JSONObject(json);
    }

    private static YoMessageRequest request() {
        return YoMessageRequest.builder().model("claude").maxTokens(32).addUserMessage("Hello").build();
    }
}
