package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoSpecSource;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.platform.anthropic.AnthropicPlatform;
import com.yovo.lib_ai.platform.deepseek.DeepSeekPlatform;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class ModelDirectoryTest {

    private static final YoServiceTarget TARGET = new YoServiceTarget("https://example.com", Map.of());

    @Test
    public void openAiShapeKeepsStandardFieldsAndReadsExtendedOnes() {
        YoModelPage page = new OpenAiModelListing(TARGET).decode("""
                {"object":"list","data":[
                  {"id":"gpt-4o","object":"model","created":1715367049,"owned_by":"system"},
                  {"id":"text-embedding-3-small","object":"model","created":1705948997,"owned_by":"system"},
                  {"id":"whisper-1","object":"model","created":1677532384,"owned_by":"openai-internal"},
                  {"id":"o3-mini","object":"model","created":1737146383,"owned_by":"system"},
                  {"id":"sensenova-6.7-flash-lite","context_length":131072,"max_output_length":8192,
                   "input_modalities":["text","image"],"output_modalities":["text"],"supported_features":["reasoning"]},
                  {"id":"sensenova-u2","output_modalities":["image"]},
                  {"id":"gpt-4o","object":"model"}
                ]}
                """);

        List<YoModelInfo> models = page.models();
        assertEquals(6, models.size());
        assertNull(page.nextCursor());

        YoModelInfo gpt = models.get(0);
        assertEquals("gpt-4o", gpt.id());
        assertEquals("gpt-4o", gpt.displayName());
        assertEquals("system", gpt.ownedBy());
        assertEquals(1715367049000L, gpt.createdAtMillis());
        assertEquals(YoModelCategory.TEXT, gpt.spec().category());
        assertEquals("平台只给了 id，类别只能是推断", YoSpecSource.INFERRED, gpt.spec().categorySource());
        assertNull(gpt.spec().limits().contextWindow());

        assertEquals(YoModelCategory.EMBEDDING, models.get(1).spec().category());
        assertEquals(YoModelCategory.SPEECH_RECOGNITION, models.get(2).spec().category());
        assertTrue(models.get(3).spec().reasons());
        assertEquals("o3-mini 的推理只是名字上的提示", YoSpecSource.INFERRED,
                models.get(3).spec().reasoning().source());

        YoModelInfo sense = models.get(4);
        assertEquals(Integer.valueOf(131072), sense.spec().limits().contextWindow());
        assertEquals(Integer.valueOf(8192), sense.spec().limits().maxOutputTokens());
        assertTrue(sense.spec().input(YoModality.IMAGE).confirmed());
        assertTrue("平台说输入只有文本和图片，其余就是不支持", sense.spec().input(YoModality.AUDIO).no());
        assertTrue(sense.spec().reasoning().confirmed());
        assertEquals("平台报了能收图片，类别随之升到视觉", YoModelCategory.VISION, sense.spec().category());
        assertEquals(YoSpecSource.REPORTED, sense.spec().categorySource());

        assertEquals(YoModelCategory.IMAGE_GENERATION, models.get(5).spec().category());
        assertEquals(YoSpecSource.REPORTED, models.get(5).spec().categorySource());
        assertTrue(models.get(0).rawJson().contains("owned_by"));
    }

    @Test
    public void openAiShapeAcceptsBareArraysAndStringEntries() {
        assertEquals(2, new OpenAiModelListing(TARGET).decode("[\"a\",{\"id\":\"b\"}]").models().size());
        assertEquals(1, new OpenAiModelListing(TARGET).decode("{\"models\":[{\"name\":\"llama3\"}]}").models().size());
    }

    @Test
    public void openAiShapeRejectsBodiesThatAreNotLists() {
        for (String body : new String[] {"", "not json", "{\"error\":{\"message\":\"x\"}}"}) {
            try {
                new OpenAiModelListing(TARGET).decode(body);
                fail("expected protocol error for: " + body);
            } catch (YoAiProtocolException expected) {
                assertEquals(YoAiErrorKind.PROTOCOL, YoAiErrors.kindOf(expected));
            }
        }
    }

    @Test
    public void anthropicShapeDecodesCapabilitiesDropsRetiredAndPages() {
        AnthropicModelListing listing = new AnthropicModelListing(TARGET);
        YoModelPage first = listing.decode("""
                {"data":[
                  {"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5",
                   "created_at":"2026-09-21T00:00:00Z","max_input_tokens":200000,"max_tokens":64000,
                   "lifecycle":"active",
                   "capabilities":{"image_input":{"supported":true},"thinking":{"supported":true}}},
                  {"type":"model","id":"claude-old","display_name":"Old","lifecycle":"retired"},
                  {"type":"model","id":"claude-haiku-5-5","display_name":"Claude Haiku 5.5","max_input_tokens":null,
                   "capabilities":{"image_input":{"supported":false}}}
                ],"first_id":"claude-opus-5-5","has_more":true,"last_id":"claude-haiku-5-5"}
                """);

        assertEquals(2, first.models().size());
        assertEquals("claude-haiku-5-5", first.nextCursor());
        YoModelInfo opus = first.models().get(0);
        assertEquals("Claude Opus 5.5", opus.displayName());
        assertEquals(Integer.valueOf(200000), opus.spec().limits().contextWindow());
        assertEquals(Integer.valueOf(64000), opus.spec().limits().maxOutputTokens());
        assertTrue(opus.createdAtMillis() > 0);
        assertTrue(opus.spec().input(YoModality.IMAGE).confirmed());
        assertTrue(opus.spec().reasoning().confirmed());
        assertEquals(YoModelCategory.VISION, opus.spec().category());
        YoModelInfo haiku = first.models().get(1);
        assertNull(haiku.spec().limits().contextWindow());
        assertFalse(haiku.spec().accepts(YoModality.IMAGE));
        assertTrue("平台明确说不支持，不是没说", haiku.spec().input(YoModality.IMAGE).no());
        assertEquals("Messages 列表里的全是对话模型，这一点是平台给的", YoModelCategory.TEXT, haiku.spec().category());
        assertEquals(YoSpecSource.REPORTED, haiku.spec().categorySource());

        assertEquals("/v1/models?limit=1000", listing.path(null));
        assertEquals("/v1/models?limit=1000&after_id=a%2Fb+c", listing.path("a/b c"));
        assertNull(listing.decode("{\"data\":[],\"has_more\":false,\"last_id\":\"x\"}").nextCursor());
    }

    @Test
    public void clientListsOpenAiModelsWithBearerKey() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("""
                    {"object":"list","data":[{"id":"deepseek-chat","owned_by":"deepseek"},{"id":"deepseek-reasoner"}]}
                    """));
            YoAiClient client = YoAiClient.builder()
                    .platform(DeepSeekPlatform.builder().baseUrl(baseUrl(server)).apiKey("k-1").build())
                    .callbackExecutor(Runnable::run)
                    .build();
            try {
                List<YoModelInfo> models = client.models().list();
                assertEquals(2, models.size());
                assertTrue(models.get(1).spec().reasons());
                RecordedRequest request = server.takeRequest();
                assertEquals("GET", request.getMethod());
                assertEquals("/v1/models", request.getPath());
                assertEquals("Bearer k-1", request.getHeader("Authorization"));
            } finally {
                client.close();
            }
        }
    }

    @Test
    public void clientFollowsAnthropicCursorsAndDeduplicates() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("""
                    {"data":[{"id":"claude-a"},{"id":"claude-b"}],"has_more":true,"last_id":"claude-b"}
                    """));
            server.enqueue(new MockResponse().setBody("""
                    {"data":[{"id":"claude-b"},{"id":"claude-c"}],"has_more":false,"last_id":"claude-c"}
                    """));
            YoAiClient client = YoAiClient.builder()
                    .platform(AnthropicPlatform.builder().baseUrl(baseUrl(server)).apiKey("ak").build())
                    .callbackExecutor(Runnable::run)
                    .build();
            try {
                List<YoModelInfo> models = client.models().list();
                assertEquals(List.of("claude-a", "claude-b", "claude-c"),
                        models.stream().map(YoModelInfo::id).toList());
                RecordedRequest first = server.takeRequest();
                assertEquals("/v1/models?limit=1000", first.getPath());
                assertEquals("ak", first.getHeader("x-api-key"));
                assertEquals("2023-06-01", first.getHeader("anthropic-version"));
                assertEquals("/v1/models?limit=1000&after_id=claude-b", server.takeRequest().getPath());
            } finally {
                client.close();
            }
        }
    }

    @Test
    public void clientStopsWhenAHostRepeatsItsCursor() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            for (int i = 0; i < 5; i++) {
                server.enqueue(new MockResponse().setBody("""
                        {"data":[{"id":"claude-a"}],"has_more":true,"last_id":"claude-a"}
                        """));
            }
            YoAiClient client = YoAiClient.builder()
                    .platform(AnthropicPlatform.builder().baseUrl(baseUrl(server)).apiKey("ak").build())
                    .callbackExecutor(Runnable::run)
                    .build();
            try {
                assertEquals(1, client.models().list().size());
                assertEquals(2, server.getRequestCount());
            } finally {
                client.close();
            }
        }
    }

    @Test
    public void clientReportsRejectedKeysAsAuthenticationErrors() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(401).setBody("""
                    {"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}
                    """));
            YoAiClient client = YoAiClient.builder()
                    .platform(OpenAiPlatform.builder().baseUrl(baseUrl(server)).apiKey("bad").build())
                    .callbackExecutor(Runnable::run)
                    .build();
            try {
                client.models().list();
                fail("expected the rejected key to surface");
            } catch (YoAiHttpException exception) {
                assertEquals(401, exception.statusCode());
                assertEquals(YoAiErrorKind.AUTHENTICATION, YoAiErrors.kindOf(exception));
                assertFalse(String.valueOf(exception.getMessage()).contains("bad"));
            } finally {
                client.close();
            }
        }
    }

    @Test
    public void platformsWithoutListingHaveNoModelService() {
        YoAiClient client = YoAiClient.builder()
                .platform(new NoListingPlatform(OpenAiPlatform.builder().apiKey("k").build()))
                .build();
        try {
            client.models();
            fail("expected no model service");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("model listing"));
        } finally {
            client.close();
        }
    }

    private static String baseUrl(MockWebServer server) {
        return server.url("/").toString().replaceAll("/+$", "");
    }
}
