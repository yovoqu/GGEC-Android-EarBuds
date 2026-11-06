package com.yovo.lib_ai.retrieval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.internal.retrieval.EmbeddingCodec;
import com.yovo.lib_ai.internal.retrieval.OpenAiEmbeddingsResource;
import com.yovo.lib_ai.internal.retrieval.QwenEmbeddingsResource;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class EmbeddingsTest {

    @Test
    public void encodesOpenAiRequestMatchingFixture() throws Exception {
        YoEmbeddingRequest request = YoEmbeddingRequest.builder()
                .model("text-embedding-3-small")
                .inputs(List.of("hello", "world"))
                .dimensions(4)
                .encodingFormat("float")
                .build();
        JSONObject encoded = EmbeddingCodec.encodeOpenAi(request);
        assertJsonEquals(new JSONObject(fixture("f13/openai_request.json")), encoded);
    }

    @Test
    public void encodesQwenRequestMatchingFixture() throws Exception {
        YoEmbeddingRequest request = YoEmbeddingRequest.builder()
                .model("text-embedding-v3")
                .inputs(List.of("hello", "world"))
                .dimensions(4)
                .build();
        JSONObject encoded = EmbeddingCodec.encodeQwen(request);
        assertJsonEquals(new JSONObject(fixture("f13/qwen_request.json")), encoded);
    }

    @Test
    public void singleInputUsesStringShape() throws Exception {
        YoEmbeddingRequest request = YoEmbeddingRequest.builder()
                .model("text-embedding-3-small")
                .input("only")
                .build();
        Object input = EmbeddingCodec.encodeOpenAi(request).get("input");
        assertTrue(input instanceof String);
        assertEquals("only", input);
    }

    @Test
    public void rejectsEmptyInputAndBadDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> YoEmbeddingRequest.builder().model("m").build());
        assertThrows(IllegalArgumentException.class,
                () -> YoEmbeddingRequest.builder().model("m").input("x").dimensions(0).build());
    }

    @Test
    public void decodesOpenAiResponseMatchingFixture() {
        YoEmbeddingResult result = EmbeddingCodec.decodeOpenAi(fixture("f13/openai_response.json"));
        assertEquals(2, result.size());
        assertEquals(4, result.vector(0).size());
        assertEquals(0.1f, result.vector(0).get(0), 1e-6);
        assertEquals(0.8f, result.vector(1).get(3), 1e-6);
        assertEquals(Integer.valueOf(4), result.totalTokens());
        assertEquals(Integer.valueOf(4), result.promptTokens());
        assertEquals("text-embedding-3-small", result.model());
    }

    @Test
    public void decodesQwenResponseAndSortsByIndex() {
        YoEmbeddingResult result = EmbeddingCodec.decodeQwen(fixture("f13/qwen_response.json"));
        assertEquals(2, result.size());
        // 服务端乱序返回 text_index 1、0，解码后必须按 0、1 排好。
        assertEquals(0, result.embeddings().get(0).index());
        assertEquals(1, result.embeddings().get(1).index());
        assertEquals(0.1f, result.vector(0).get(0), 1e-6);
        assertEquals(0.8f, result.vector(1).get(3), 1e-6);
        assertEquals(Integer.valueOf(4), result.totalTokens());
    }

    @Test
    public void postsOpenAiEmbeddingsAndDecodes() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(fixture("f13/openai_response.json")));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                OpenAiEmbeddingsResource resource = new OpenAiEmbeddingsResource(
                        client, target(server, "Bearer test-key"), new CallRegistry());
                YoEmbeddingResult result = resource.embed(YoEmbeddingRequest.builder()
                        .model("text-embedding-3-small")
                        .inputs(List.of("hello", "world"))
                        .dimensions(4)
                        .build());
                assertEquals(2, result.size());
                assertEquals(4, result.vector(0).size());
                RecordedRequest recorded = server.takeRequest();
                assertEquals("POST", recorded.getMethod());
                assertEquals("/v1/embeddings", recorded.getPath());
                assertEquals("Bearer test-key", recorded.getHeader("Authorization"));
                String body = recorded.getBody().readUtf8();
                assertTrue(body.contains("\"dimensions\":4"));
                assertTrue(body.contains("\"input\":[\"hello\",\"world\"]"));
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    @Test
    public void qwenResourceUsesNativePath() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(fixture("f13/qwen_response.json")));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                QwenEmbeddingsResource resource = new QwenEmbeddingsResource(
                        client, target(server, "Bearer qwen-key"), new CallRegistry());
                resource.embed(YoEmbeddingRequest.builder()
                        .model("text-embedding-v3").inputs(List.of("hello", "world")).dimensions(4).build());
                RecordedRequest recorded = server.takeRequest();
                assertEquals("/api/v1/services/embeddings/text-embedding/text-embedding", recorded.getPath());
                String body = recorded.getBody().readUtf8();
                assertTrue(body.contains("\"texts\""));
                assertTrue(body.contains("\"dimension\":4"));
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    @Test
    public void reportsHttpErrorWithoutLeakingKey() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(400).setBody(
                    "{\"error\":{\"message\":\"bad input\",\"type\":\"invalid_request_error\"}}"));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                OpenAiEmbeddingsResource resource = new OpenAiEmbeddingsResource(
                        client, target(server, "Bearer secret-key"), new CallRegistry());
                YoAiHttpException error = assertThrows(YoAiHttpException.class, () -> resource.embed(
                        YoEmbeddingRequest.builder().model("m").input("x").build()));
                assertEquals(400, error.statusCode());
                assertEquals("bad input", error.getMessage());
                assertFalse(String.valueOf(error.getMessage()).contains("secret-key"));
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    private static YoServiceTarget target(MockWebServer server, String authorization) {
        String baseUrl = server.url("/").toString().replaceAll("/+$", "");
        return new YoServiceTarget(baseUrl, Map.of("Authorization", authorization));
    }

    static String fixture(String path) {
        try (var stream = Objects.requireNonNull(
                EmbeddingsTest.class.getClassLoader().getResourceAsStream("fixtures/" + path))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Missing fixture " + path, exception);
        }
    }

    /**
     * 不用 org.json 的 {@code similar()}（android.jar 的桩没有它），改用 keys() 递归比较。
     */
    static void assertJsonEquals(JSONObject expected, JSONObject actual) {
        assertJsonEquals("$", expected, actual);
    }

    private static void assertJsonEquals(String path, JSONObject expected, JSONObject actual) {
        java.util.Set<String> expectedKeys = new java.util.TreeSet<>();
        for (java.util.Iterator<String> it = expected.keys(); it.hasNext(); ) {
            expectedKeys.add(it.next());
        }
        java.util.Set<String> actualKeys = new java.util.TreeSet<>();
        for (java.util.Iterator<String> it = actual.keys(); it.hasNext(); ) {
            actualKeys.add(it.next());
        }
        assertEquals("keys at " + path, expectedKeys, actualKeys);
        for (String key : expectedKeys) {
            compare(path + "." + key, expected.opt(key), actual.opt(key));
        }
    }

    @SuppressWarnings("unchecked")
    private static void compare(String path, Object expected, Object actual) {
        if (expected instanceof JSONObject expectedObject) {
            assertTrue("object at " + path, actual instanceof JSONObject);
            assertJsonEquals(path, expectedObject, (JSONObject) actual);
            return;
        }
        if (expected instanceof org.json.JSONArray expectedArray) {
            assertTrue("array at " + path, actual instanceof org.json.JSONArray);
            org.json.JSONArray actualArray = (org.json.JSONArray) actual;
            assertEquals("length at " + path, expectedArray.length(), actualArray.length());
            for (int i = 0; i < expectedArray.length(); i++) {
                compare(path + "[" + i + "]", expectedArray.opt(i), actualArray.opt(i));
            }
            return;
        }
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            assertEquals("number at " + path, expectedNumber.doubleValue(), actualNumber.doubleValue(), 1e-9);
            return;
        }
        assertEquals("value at " + path, String.valueOf(expected), String.valueOf(actual));
    }
}
