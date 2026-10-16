package com.yovo.lib_ai.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;
import com.yovo.lib_ai.platform.deepseek.DeepSeekChatPartEncoder;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.internal.chat.ChatAccumulator;
import com.yovo.lib_ai.internal.chat.ChatCodec;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

public final class ChatCompletionsTest {

    @Test
    public void encodesToolsAndDecodesCompletion() throws Exception {
        JSONObject parameters = new JSONObject().put("type", "object");
        YoChatCompletionRequest request = YoChatCompletionRequest.builder()
                .model("gpt-4o")
                .addUserMessage("Hello")
                .temperature(0.5)
                .maxCompletionTokens(32)
                .addTool(YoChatTool.function("get_weather", "lookup", parameters, true))
                .toolChoice(YoChatToolChoice.function("get_weather"))
                .responseFormat(YoChatResponseFormat.jsonObject())
                .addStop("END")
                .parallelToolCalls(false)
                .reasoningEffort("low")
                .build();

        JSONObject json = ChatCodec.encode(request, false);
        assertEquals("gpt-4o", json.getString("model"));
        assertFalse(json.has("stream"));
        JSONObject streaming = ChatCodec.encode(request, true);
        assertTrue(streaming.getBoolean("stream"));
        assertTrue(streaming.getJSONObject("stream_options").getBoolean("include_usage"));
        assertEquals("function", json.getJSONObject("tool_choice").getString("type"));
        assertEquals("get_weather", json.getJSONObject("tool_choice").getJSONObject("function").getString("name"));
        assertEquals("json_object", json.getJSONObject("response_format").getString("type"));

        YoChatCompletion completion = ChatCodec.decodeCompletion("""
                {"id":"chatcmpl-1","model":"gpt-4o","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"Hi"}}],"usage":{"prompt_tokens":3,"completion_tokens":1,"total_tokens":4},"future_field":true}
                """);
        assertEquals("Hi", completion.text());
        assertEquals(4, completion.usage().totalTokens());
        assertTrue(completion.rawJson().contains("future_field"));
    }

    @Test
    public void encodesImageAndFileParts() throws Exception {
        YoChatCompletionRequest request = YoChatCompletionRequest.builder()
                .model("sensenova-6.8-flash-lite")
                .addMessage(YoChatMessage.builder()
                        .role("user")
                        .parts(
                                YoChatContentPart.text("图片里面有什么"),
                                YoChatContentPart.imageUrl("https://example.com/a.png", "low"),
                                YoChatContentPart.imageUrl("data:image/png;base64,aaaa"),
                                YoChatContentPart.fileId("file-api-1"),
                                YoChatContentPart.fileData("bbbb", "a.pdf"))
                        .build())
                .build();
        JSONArray content = ChatCodec.encode(request, false)
                .getJSONArray("messages")
                .getJSONObject(0)
                .getJSONArray("content");
        assertEquals("text", content.getJSONObject(0).getString("type"));
        JSONObject image = content.getJSONObject(1).getJSONObject("image_url");
        assertEquals("https://example.com/a.png", image.getString("url"));
        assertEquals("low", image.getString("detail"));
        assertEquals("data:image/png;base64,aaaa", content.getJSONObject(2).getJSONObject("image_url").getString("url"));
        assertEquals("file-api-1", content.getJSONObject(3).getJSONObject("file").getString("file_id"));
        assertEquals("a.pdf", content.getJSONObject(4).getJSONObject("file").getString("filename"));
        assertEquals("bbbb", content.getJSONObject(4).getJSONObject("file").getString("file_data"));
        JSONArray deepSeek = ChatCodec.encode(request, false, DeepSeekChatPartEncoder.INSTANCE)
                .getJSONArray("messages")
                .getJSONObject(0)
                .getJSONArray("content");
        assertEquals("file-api-1", deepSeek.getJSONObject(3).getString("file_id"));
        assertFalse(deepSeek.getJSONObject(3).has("file"));
    }

    @Test
    public void accumulatesToolCallDeltas() {
        ChatAccumulator accumulator = new ChatAccumulator();
        accumulator.add(ChatCodec.decodeChunk("""
                {"id":"c1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"get_weather","arguments":""}}]}}]}
                """));
        accumulator.add(ChatCodec.decodeChunk("""
                {"id":"c1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"city\\":\\"SZ\\"}"}}]},"finish_reason":"tool_calls"}]}
                """));
        YoChatCompletion completion = accumulator.toCompletion();
        YoChatToolCall call = completion.choices().get(0).message().toolCalls()[0];
        assertEquals("call_1", call.id());
        assertEquals("get_weather", call.name());
        assertEquals("{\"city\":\"SZ\"}", call.arguments());
        assertEquals("tool_calls", completion.choices().get(0).finishReason());
    }

    @Test
    public void postsChatCompletionsAndStreams() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("""
                    {"id":"chatcmpl-1","model":"gpt-4o","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"Hi"}}]}
                    """));
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"id":"chatcmpl-2","choices":[{"index":0,"delta":{"content":"He"}}]}

                            data: {"id":"chatcmpl-2","choices":[{"index":0,"delta":{"content":"llo"},"finish_reason":"stop"}]}

                            data: [DONE]

                            """));
            YoAiClient client = client(server);
            try {
                YoChatCompletion completion = client.chatCompletions().complete(request());
                assertEquals("Hi", completion.text());
                RecordedRequest recorded = server.takeRequest();
                assertEquals("/v1/chat/completions", recorded.getPath());
                assertEquals("Bearer test-key", recorded.getHeader("Authorization"));
                assertTrue(recorded.getBody().readUtf8().contains("\"model\":\"gpt-4o\""));

                CountDownLatch done = new CountDownLatch(1);
                AtomicReference<String> text = new AtomicReference<>();
                client.chatCompletions().stream(request(), new YoChatStreamListener() {
                    @Override
                    public void onChunk(YoChatChunk chunk) {
                    }

                    @Override
                    public void onComplete(YoChatCompletion completion) {
                        text.set(completion.text());
                        done.countDown();
                    }

                    @Override
                    public void onFailure(com.yovo.lib_ai.error.YoAiException error) {
                        done.countDown();
                    }
                });
                assertTrue(done.await(5, TimeUnit.SECONDS));
                assertEquals("Hello", text.get());
            } finally {
                client.close();
            }
        }
    }

    @Test
    public void retries429AndDoesNotRetry401() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody("""
                    {"error":{"message":"slow down","type":"rate_limit"}}
                    """));
            server.enqueue(new MockResponse().setBody("""
                    {"id":"ok","choices":[{"index":0,"message":{"role":"assistant","content":"Go"}}]}
                    """));
            YoAiClient client = client(server);
            try {
                assertEquals("Go", client.chatCompletions().complete(request()).text());
                assertEquals(2, server.getRequestCount());
            } finally {
                client.close();
            }
        }
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(401).setHeader("x-request-id", "req_1").setBody("""
                    {"error":{"message":"bad key","type":"authentication_error"}}
                    """));
            YoAiClient client = client(server);
            boolean thrown = false;
            try {
                client.chatCompletions().complete(request());
            } catch (YoAiHttpException exception) {
                thrown = true;
                assertEquals(401, exception.statusCode());
                assertEquals("req_1", exception.requestId());
                assertEquals("bad key", exception.getMessage());
                assertFalse(exception.getMessage().contains("test-key"));
                assertFalse(String.valueOf(exception.responseBody()).contains("test-key"));
            } finally {
                client.close();
            }
            assertTrue(thrown);
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    public void cancelReleasesAStalledStream() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.NO_RESPONSE));
            YoAiClient client = client(server);
            client.chatCompletions().stream(request(), new YoChatStreamListener() {
                @Override
                public void onChunk(YoChatChunk chunk) {
                }

                @Override
                public void onComplete(YoChatCompletion completion) {
                }

                @Override
                public void onFailure(com.yovo.lib_ai.error.YoAiException error) {
                }
            });
            long started = System.nanoTime();
            client.close();
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(3));
        }
    }

    private static YoChatCompletionRequest request() {
        return YoChatCompletionRequest.builder().model("gpt-4o").addUserMessage("Hello").build();
    }

    private static YoAiClient client(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/+$", "");
        return YoAiClient.builder()
                .platform(OpenAiPlatform.builder().baseUrl(baseUrl).apiKey("test-key").build())
                .maxRetries(2)
                .initialRetryDelayMillis(0)
                .callbackExecutor(Runnable::run)
                .build();
    }
}
