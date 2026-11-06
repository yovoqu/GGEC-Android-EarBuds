package com.yovo.lib_ai.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.messages.MessagesCodec;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MessagesThinkingTest {

    @Test
    public void encodesThinkingCacheAndOutputConfig() throws Exception {
        YoMessageRequest request = YoMessageRequest.builder()
                .model("claude-sonnet-4-5")
                .maxTokens(1024)
                .system("be brief")
                .cacheControl(new JSONObject().put("type", "ephemeral"))
                .addMessage(YoMessageTurn.of("user", List.of(
                        YoMessageContent.text("hello").withCache(new JSONObject().put("type", "ephemeral")))))
                .thinking(YoThinkingConfig.adaptive(YoThinkingConfig.Display.SUMMARIZED))
                .outputConfig(new JSONObject().put("format",
                        new JSONObject().put("type", "json_schema").put("schema", new JSONObject().put("type", "object"))))
                .userId("u1")
                .metadata(new JSONObject().put("trace", "t1"))
                .serviceTier("auto")
                .build();

        JSONObject actual = MessagesCodec.encode(request, false);
        JSONObject expected = fixture("f09/request_thinking.json");
        assertTrue("thinking/cache/output_config must match: " + actual,
                expected.toString().equals(actual.toString()));

        JSONObject thinking = actual.getJSONObject("thinking");
        assertEquals("adaptive", thinking.getString("type"));
        assertEquals("summarized", thinking.getString("display"));
        assertEquals("ephemeral", actual.getJSONArray("system").getJSONObject(0)
                .getJSONObject("cache_control").getString("type"));
        assertEquals("ephemeral", actual.getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content").getJSONObject(0).getJSONObject("cache_control").getString("type"));
        assertEquals("json_schema", actual.getJSONObject("output_config")
                .getJSONObject("format").getString("type"));
        assertEquals("u1", actual.getJSONObject("metadata").getString("user_id"));
        assertEquals("t1", actual.getJSONObject("metadata").getString("trace"));
    }

    @Test
    public void enabledThinkingCarriesBudgetTokens() throws Exception {
        YoMessageRequest request = YoMessageRequest.builder()
                .model("claude-sonnet-4-5")
                .maxTokens(512)
                .addUserMessage("hi")
                .thinking(YoThinkingConfig.enabled(2048))
                .build();
        JSONObject thinking = MessagesCodec.encode(request, false).getJSONObject("thinking");
        assertEquals("enabled", thinking.getString("type"));
        assertEquals(2048, thinking.getInt("budget_tokens"));
    }

    @Test
    public void decodesThinkingAndTextBlocks() throws Exception {
        YoMessage message = MessagesCodec.decode(fixture("f09/response_thinking.json").toString());
        assertEquals(2, message.content().size());
        YoMessageContent thinking = message.content().get(0);
        assertEquals(YoMessageContent.Type.THINKING, thinking.type());
        assertEquals("plan", thinking.text());
        assertEquals("sig", thinking.signature());
        assertEquals("done", message.content().get(1).text());
        assertEquals("done", message.text());
    }

    private static JSONObject fixture(String path) throws Exception {
        try (InputStream stream = MessagesThinkingTest.class.getResourceAsStream("/fixtures/" + path)) {
            if (stream == null) {
                throw new IllegalStateException("missing fixture " + path);
            }
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
