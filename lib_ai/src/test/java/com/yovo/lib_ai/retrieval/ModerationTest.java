package com.yovo.lib_ai.retrieval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.retrieval.ModerationCodec;
import com.yovo.lib_ai.internal.retrieval.OpenAiModerationResource;
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

public final class ModerationTest {

    @Test
    public void encodesSingleTextAsString() throws Exception {
        YoModerationRequest request = YoModerationRequest.builder()
                .model("omni-moderation-latest")
                .input("hello")
                .build();
        JSONObject encoded = ModerationCodec.encode(request);
        assertEquals("hello", encoded.get("input"));
        assertEquals("omni-moderation-latest", encoded.getString("model"));
    }

    @Test
    public void encodesMultimodalInputAsArray() throws Exception {
        YoModerationRequest request = YoModerationRequest.builder()
                .input("text")
                .addInput(new JSONObject().put("type", "image_url")
                        .put("image_url", new JSONObject().put("url", "https://example.com/a.png")))
                .build();
        JSONObject encoded = ModerationCodec.encode(request);
        assertEquals(2, encoded.getJSONArray("input").length());
    }

    @Test
    public void decodesFlaggedResultWithPerCategoryScores() {
        YoModerationResult result = ModerationCodec.decode(EmbeddingsTest.fixture("f18/response.json"));
        assertTrue(result.flagged());
        assertEquals(0.88, result.score("violence"), 1e-6);
        assertEquals(0.01, result.score("hate"), 1e-6);
        assertEquals("omni-moderation-latest", result.model());
        YoModerationCategory violence = result.categories().stream()
                .filter(category -> category.name().equals("violence"))
                .findFirst().orElse(null);
        assertNotNull(violence);
        assertTrue(violence.flagged());
        // 多模态分开计分：violence 同时作用于文本与图片。
        assertTrue(violence.appliedInputTypes().contains("text"));
        assertTrue(violence.appliedInputTypes().contains("image"));
        assertFalse(result.categories().isEmpty());
    }

    @Test
    public void postsToModerations() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f18/response.json")));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                OpenAiModerationResource resource = new OpenAiModerationResource(
                        client, target(server), new CallRegistry());
                YoModerationResult result = resource.moderate(
                        YoModerationRequest.builder().input("bad text").build());
                assertTrue(result.flagged());
                RecordedRequest recorded = server.takeRequest();
                assertEquals("POST", recorded.getMethod());
                assertEquals("/v1/moderations", recorded.getPath());
                assertTrue(recorded.getBody().readUtf8().contains("\"input\":\"bad text\""));
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    private static YoServiceTarget target(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/+$", "");
        return new YoServiceTarget(baseUrl, Map.of("Authorization", "Bearer mod-key"));
    }
}
