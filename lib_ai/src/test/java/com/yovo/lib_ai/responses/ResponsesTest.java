package com.yovo.lib_ai.responses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.api.YoResponsesConnection;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;
import com.yovo.lib_ai.platform.sensenova.SenseNovaResponsesPolicy;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.internal.responses.ResponsesCodec;
import com.yovo.lib_ai.internal.responses.ResponsesLaneTable;
import com.yovo.lib_ai.internal.responses.ResponsesReducer;

import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class ResponsesTest {

    @Test
    public void encodesResponsesRequestWithoutTransportFields() throws Exception {
        YoResponseRequest request = YoResponseRequest.builder()
                .model("gpt-4o")
                .instructions("be brief")
                .previousResponseId("resp_prev")
                .addInput(YoResponseInput.userText("Hello"))
                .addInput(YoResponseInput.functionCallOutput("call_1", "{\"ok\":true}"))
                .addTool(YoResponseTool.function("get_weather", "lookup", new JSONObject().put("type", "object")))
                .toolChoice(YoResponseToolChoice.function("get_weather"))
                .store(false)
                .maxOutputTokens(40)
                .textFormat(YoResponseTextFormat.jsonSchema("answer", new JSONObject().put("type", "object"), true))
                .generate(false)
                .build();

        JSONObject http = ResponsesCodec.encodeHttp(request, true);
        assertEquals(true, http.getBoolean("stream"));
        assertFalse(http.has("background"));
        assertFalse(http.has("generate"));
        assertEquals("function", http.getJSONArray("tools").getJSONObject(0).getString("type"));
        assertEquals("get_weather", http.getJSONObject("tool_choice").getString("name"));

        JSONObject socket = ResponsesCodec.encodeWebSocket(request, "main");
        assertEquals("response.create", socket.getString("type"));
        assertEquals("main", socket.getString("stream_id"));
        assertEquals(false, socket.getBoolean("generate"));
        assertFalse(socket.has("stream"));
        assertFalse(socket.has("background"));
    }

    @Test
    public void encodesInputImageParts() throws Exception {
        YoResponseRequest request = YoResponseRequest.builder()
                .model("sensenova-6.8-flash-lite")
                .addInput(YoResponseInput.message(
                        "user",
                        YoResponseContentPart.text("图片里面有什么"),
                        YoResponseContentPart.imageUrl("https://example.com/a.png", "low"),
                        YoResponseContentPart.imageUrl("data:image/webp;base64,cccc"),
                        YoResponseContentPart.imageFile("file-api-1")))
                .build();
        org.json.JSONArray content = ResponsesCodec.encodeHttp(request, false)
                .getJSONArray("input")
                .getJSONObject(0)
                .getJSONArray("content");
        assertEquals("input_text", content.getJSONObject(0).getString("type"));
        assertEquals("input_image", content.getJSONObject(1).getString("type"));
        assertEquals("https://example.com/a.png", content.getJSONObject(1).getString("image_url"));
        assertEquals("low", content.getJSONObject(1).getString("detail"));
        assertEquals("data:image/webp;base64,cccc", content.getJSONObject(2).getString("image_url"));
        assertEquals("file-api-1", content.getJSONObject(3).getString("file_id"));
        assertFalse(content.getJSONObject(3).has("image_url"));
    }

    @Test
    public void accumulatorPrefersCompletedSnapshot() throws Exception {
        ResponsesReducer accumulator = new ResponsesReducer();
        accumulator.apply(new JSONObject("""
                {"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":"Hi"}
                """));
        accumulator.apply(new JSONObject("""
                {"type":"response.completed","response":{"id":"resp_1","status":"completed","model":"gpt-4o","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Hi"}]}],"usage":{"input_tokens":2,"output_tokens":1,"total_tokens":3}}}
                """));
        YoResponseObject response = accumulator.toObject();
        assertEquals("resp_1", response.id());
        assertEquals("Hi", response.text());
        assertEquals(3, response.usage().totalTokens());
    }

    @Test
    public void steeredIncompleteKeepsTheLaneBusy() throws Exception {
        ResponsesLaneTable lanes = new ResponsesLaneTable();
        lanes.begin("main", listener(new AtomicReference<>(), new CountDownLatch(1)));
        ResponsesLaneTable.ResponsesDispatch steered = lanes.push("main", new JSONObject("""
                {"type":"response.incomplete","stream_id":"main","response":{"id":"resp_1","status":"incomplete","incomplete_details":{"reason":"steered"},"output":[]}}
                """));
        assertEquals("incomplete", steered.completed.status());
        boolean busy = false;
        try {
            lanes.begin("main", new YoResponseStreamListener() {
                @Override
                public void onEvent(YoResponseStreamEvent event) {
                }

                @Override
                public void onComplete(YoResponseObject response) {
                }

                @Override
                public void onFailure(YoAiException error) {
                }
            });
        } catch (IllegalStateException exception) {
            busy = true;
        }
        assertTrue(busy);
        assertTrue(lanes.isOpen("main"));
    }

    @Test
    public void reducerKeepsFunctionCallArgumentsAndReasoningApart() throws Exception {
        ResponsesReducer reducer = new ResponsesReducer();
        reducer.apply(new JSONObject("""
                {"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","name":"get_weather","call_id":"call_1","arguments":""}}
                """));
        reducer.apply(new JSONObject("""
                {"type":"response.function_call_arguments.delta","output_index":0,"delta":"{\\"city\\":"}
                """));
        reducer.apply(new JSONObject("""
                {"type":"response.function_call_arguments.done","output_index":0,"arguments":"{\\"city\\":\\"SZ\\"}"}
                """));
        reducer.apply(new JSONObject("""
                {"type":"response.output_item.added","output_index":1,"item":{"type":"reasoning","summary":[]}}
                """));
        reducer.apply(new JSONObject("""
                {"type":"response.reasoning_summary_text.delta","output_index":1,"summary_index":0,"delta":"think"}
                """));
        YoResponseObject response = reducer.toObject();
        assertEquals("function_call", response.output().get(0).type());
        assertEquals("get_weather", response.output().get(0).name());
        assertEquals("{\"city\":\"SZ\"}", response.output().get(0).arguments());
        assertEquals("", response.text());
        assertTrue(response.output().get(1).rawJson().contains("think"));
    }

    @Test
    public void closingDoesNotFailAFinishedLane() throws Exception {
        ResponsesLaneTable lanes = new ResponsesLaneTable();
        lanes.begin("main", listener(new AtomicReference<>(), new CountDownLatch(1)));
        lanes.push("main", new JSONObject("""
                {"type":"response.completed","stream_id":"main","response":{"id":"resp_1","status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"Hi"}]}]}}
                """));
        assertFalse(lanes.isOpen("main"));
        assertTrue(lanes.failOpen().isEmpty());
    }

    @Test
    public void postsAndStreamsResponses() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("""
                    {"id":"resp_1","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Hi"}]}]}
                    """));
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody("""
                    event: response.output_text.delta
                    data: {"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":"Yo"}

                    event: response.completed
                    data: {"type":"response.completed","response":{"id":"resp_2","status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"Yo"}]}]}}

                    """));
            YoAiClient client = client(server);
            try {
                assertEquals("Hi", client.responses().create(request()).text());
                CountDownLatch done = new CountDownLatch(1);
                AtomicReference<String> text = new AtomicReference<>();
                client.responses().stream(request(), new YoResponseStreamListener() {
                    @Override
                    public void onEvent(YoResponseStreamEvent event) {
                    }

                    @Override
                    public void onComplete(YoResponseObject response) {
                        text.set(response.text());
                        done.countDown();
                    }

                    @Override
                    public void onFailure(YoAiException error) {
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

    @Test
    public void webSocketDemuxesStreamIds() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    try {
                        JSONObject json = new JSONObject(text);
                        String streamId = json.optString("stream_id");
                        webSocket.send(delta(streamId, streamId));
                        webSocket.send(completed(streamId));
                    } catch (org.json.JSONException exception) {
                        throw new RuntimeException(exception);
                    }
                }
            }));
            YoAiClient client = client(server);
            try {
                YoResponsesConnection connection = client.responses().connect();
                CountDownLatch done = new CountDownLatch(2);
                AtomicReference<String> planner = new AtomicReference<>();
                AtomicReference<String> research = new AtomicReference<>();
                connection.create("planner", request(), listener(planner, done));
                connection.create("research", request(), listener(research, done));
                assertTrue(done.await(5, TimeUnit.SECONDS));
                assertEquals("planner", planner.get());
                assertEquals("research", research.get());
                RecordedRequest handshake = server.takeRequest();
                assertEquals("/v1/responses", handshake.getPath());
                assertEquals("Bearer test-key", handshake.getHeader("Authorization"));
                assertTrue(handshake.getHeader("Upgrade").equalsIgnoreCase("websocket"));
                connection.close();
            } finally {
                client.close();
            }
        }
    }

    @Test
    public void webSocketSendsWhileAResponseIsOpen() throws Exception {
        AtomicBoolean plannerCompleted = new AtomicBoolean();
        AtomicBoolean sentWhileOpen = new AtomicBoolean();
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    try {
                        JSONObject json = new JSONObject(text);
                        if ("response.steer".equals(json.optString("type"))) {
                            if (!plannerCompleted.get()) {
                                sentWhileOpen.set(true);
                            }
                            webSocket.send(completed("planner"));
                            plannerCompleted.set(true);
                            return;
                        }
                        String streamId = json.optString("stream_id");
                        if ("research".equals(streamId)) {
                            if (!plannerCompleted.get()) {
                                sentWhileOpen.set(true);
                            }
                            webSocket.send(delta("research", "research"));
                            webSocket.send(completed("research"));
                            return;
                        }
                        webSocket.send(delta("planner", "P"));
                    } catch (org.json.JSONException exception) {
                        throw new RuntimeException(exception);
                    }
                }
            }));
            YoAiClient client = client(server);
            try {
                YoResponsesConnection connection = client.responses().connect();
                CountDownLatch done = new CountDownLatch(2);
                AtomicReference<String> planner = new AtomicReference<>();
                AtomicReference<String> research = new AtomicReference<>();
                AtomicBoolean forwarded = new AtomicBoolean();
                connection.create("planner", request(), new YoResponseStreamListener() {
                    @Override
                    public void onEvent(YoResponseStreamEvent event) {
                        if ("response.output_text.delta".equals(event.type()) && forwarded.compareAndSet(false, true)) {
                            connection.create("research", request(), listener(research, done));
                            connection.steer("resp_planner", "continue");
                        }
                    }

                    @Override
                    public void onComplete(YoResponseObject response) {
                        planner.set(response.text());
                        done.countDown();
                    }

                    @Override
                    public void onFailure(YoAiException error) {
                        planner.set(error.getMessage());
                        done.countDown();
                    }
                });
                assertTrue(done.await(5, TimeUnit.SECONDS));
                assertTrue(sentWhileOpen.get());
                assertEquals("planner", planner.get());
                assertEquals("research", research.get());
                connection.close();
            } finally {
                client.close();
            }
        }
    }

    @Test
    public void endpointToStringRedactsTheKey() {
        OpenAiPlatform platform = OpenAiPlatform.builder().apiKey("super-secret").build();
        assertFalse(platform.toString().contains("super-secret"));
        assertEquals("Bearer super-secret", platform.chatTarget().headers().get("Authorization"));
        SenseNovaResponsesPolicy policy = new SenseNovaResponsesPolicy(false);
        YoResponseRequest blocked = YoResponseRequest.builder()
                .model("sensenova-6.8-flash-lite")
                .previousResponseId("resp_prev")
                .input("Hello")
                .build();
        boolean rejected = false;
        try {
            policy.check(blocked);
        } catch (IllegalArgumentException exception) {
            rejected = exception.getMessage().contains("previous_response_id");
        }
        assertTrue(rejected);
    }

    private static YoResponseStreamListener listener(AtomicReference<String> text, CountDownLatch done) {
        return new YoResponseStreamListener() {
            @Override
            public void onEvent(YoResponseStreamEvent event) {
            }

            @Override
            public void onComplete(YoResponseObject response) {
                text.set(response.text());
                done.countDown();
            }

            @Override
            public void onFailure(YoAiException error) {
                text.set(error.getMessage());
                done.countDown();
            }
        };
    }

    private static String delta(String streamId, String text) {
        return "{\"type\":\"response.output_text.delta\",\"stream_id\":\""
                + streamId + "\",\"output_index\":0,\"content_index\":0,\"delta\":\"" + text + "\"}";
    }

    private static String completed(String streamId) {
        return "{\"type\":\"response.completed\",\"stream_id\":\"" + streamId
                + "\",\"response\":{\"id\":\"" + streamId + "\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\""
                + streamId + "\"}]}]}}";
    }

    private static YoResponseRequest request() {
        return YoResponseRequest.builder().model("gpt-4o").input("Hello").build();
    }

    private static YoAiClient client(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/+$", "");
        return YoAiClient.builder()
                .platform(OpenAiPlatform.builder().baseUrl(baseUrl).apiKey("test-key").build())
                .initialRetryDelayMillis(0)
                .callbackExecutor(Runnable::run)
                .build();
    }
}
