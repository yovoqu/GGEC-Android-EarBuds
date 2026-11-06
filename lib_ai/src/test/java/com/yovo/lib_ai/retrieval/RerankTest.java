package com.yovo.lib_ai.retrieval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.retrieval.QwenRerankResource;
import com.yovo.lib_ai.internal.retrieval.RerankCodec;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class RerankTest {

    @Test
    public void encodesRequestMatchingFixture() throws Exception {
        YoRerankRequest request = YoRerankRequest.builder()
                .model("gte-rerank-v2")
                .query("什么是文本排序模型")
                .documents(java.util.List.of("文本排序模型用于对候选文档排序", "今天天气不错"))
                .topN(1)
                .returnDocuments(true)
                .instruct("Given a web search query, retrieve relevant passages that answer the query.")
                .build();
        JSONObject encoded = RerankCodec.encode(request);
        EmbeddingsTest.assertJsonEquals(new JSONObject(EmbeddingsTest.fixture("f14/request.json")), encoded);
    }

    @Test
    public void decodesAndSortsByScoreDescending() {
        YoRerankResult result = RerankCodec.decode(EmbeddingsTest.fixture("f14/response.json"));
        assertEquals(2, result.results().size());
        assertEquals("高分排在前", 0, result.results().get(0).index());
        assertEquals(0.9, result.results().get(0).score(), 1e-6);
        assertEquals("今天天气不错", result.results().get(1).document());
        assertEquals(Integer.valueOf(12), result.promptTokens());
    }

    @Test
    public void truncatesToTopN() {
        YoRerankResult result = RerankCodec.decode(EmbeddingsTest.fixture("f14/response.json"));
        YoRerankResult truncated = RerankCodec.truncate(result, 1);
        assertEquals(1, truncated.results().size());
        assertEquals(0.9, truncated.results().get(0).score(), 1e-6);
    }

    @Test
    public void rejectsBadFps() {
        assertThrows(IllegalArgumentException.class,
                () -> YoRerankRequest.builder().model("m").query("q").addDocument("d").fps(2.0).build());
    }

    @Test
    public void postsRerankAndAppliesTopN() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f14/response.json")));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                QwenRerankResource resource = new QwenRerankResource(
                        client, target(server), new CallRegistry());
                YoRerankResult result = resource.rerank(YoRerankRequest.builder()
                        .model("gte-rerank-v2")
                        .query("什么是文本排序模型")
                        .documents(java.util.List.of("文本排序模型用于对候选文档排序", "今天天气不错"))
                        .topN(1)
                        .returnDocuments(true)
                        .build());
                assertEquals(1, result.results().size());
                assertEquals(0, result.results().get(0).index());
                RecordedRequest recorded = server.takeRequest();
                assertEquals("POST", recorded.getMethod());
                assertEquals("/api/v1/services/rerank/text-rerank/text-rerank", recorded.getPath());
                String body = recorded.getBody().readUtf8();
                assertTrue(body.contains("\"top_n\":1"));
                assertTrue(body.contains("\"return_documents\":true"));
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    private static YoServiceTarget target(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/+$", "");
        return new YoServiceTarget(baseUrl, Map.of("Authorization", "Bearer qwen-key"));
    }
}
