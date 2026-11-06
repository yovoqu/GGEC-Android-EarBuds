package com.yovo.lib_ai.retrieval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.retrieval.AnthropicTokenCounterResource;
import com.yovo.lib_ai.internal.retrieval.OpenAiTokenCounterResource;
import com.yovo.lib_ai.internal.retrieval.TokenCodec;
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

public final class TokenCountTest {

    @Test
    public void encodesAnthropicRequestMatchingFixture() throws Exception {
        YoTokenCountRequest request = YoTokenCountRequest.builder()
                .model("claude-sonnet-4-5")
                .addMessage(new JSONObject().put("role", "user").put("content", "Hello"))
                .system("You are helpful.")
                .build();
        JSONObject encoded = TokenCodec.encodeAnthropic(request);
        EmbeddingsTest.assertJsonEquals(new JSONObject(EmbeddingsTest.fixture("f17/anthropic_request.json")), encoded);
    }

    @Test
    public void encodesOpenAiRequestMatchingFixture() throws Exception {
        YoTokenCountRequest request = YoTokenCountRequest.builder()
                .model("gpt-4o")
                .inputText("Hello")
                .build();
        JSONObject encoded = TokenCodec.encodeOpenAi(request);
        EmbeddingsTest.assertJsonEquals(new JSONObject(EmbeddingsTest.fixture("f17/openai_request.json")), encoded);
    }

    @Test
    public void decodesBothResponses() {
        assertEquals(17, TokenCodec.decode(EmbeddingsTest.fixture("f17/anthropic_response.json")).inputTokens());
        assertEquals(8, TokenCodec.decode(EmbeddingsTest.fixture("f17/openai_response.json")).inputTokens());
    }

    @Test
    public void postsToEachEndpoint() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f17/anthropic_response.json")));
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f17/openai_response.json")));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                AnthropicTokenCounterResource anthropic = new AnthropicTokenCounterResource(
                        client, target(server), new CallRegistry());
                assertEquals(17, anthropic.count(YoTokenCountRequest.builder()
                        .model("claude-sonnet-4-5")
                        .addMessage(new JSONObject().put("role", "user").put("content", "Hello"))
                        .build()).inputTokens());
                RecordedRequest first = server.takeRequest();
                assertEquals("/v1/messages/count_tokens", first.getPath());
                assertTrue(first.getBody().readUtf8().contains("\"messages\""));

                OpenAiTokenCounterResource openAi = new OpenAiTokenCounterResource(
                        client, target(server), new CallRegistry());
                assertEquals(8, openAi.count(YoTokenCountRequest.builder()
                        .model("gpt-4o").inputText("Hello").build()).inputTokens());
                RecordedRequest second = server.takeRequest();
                assertEquals("/v1/responses/input_tokens", second.getPath());
                assertTrue(second.getBody().readUtf8().contains("\"input\":\"Hello\""));
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    private static YoServiceTarget target(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/+$", "");
        return new YoServiceTarget(baseUrl, Map.of("Authorization", "Bearer token-key"));
    }
}
