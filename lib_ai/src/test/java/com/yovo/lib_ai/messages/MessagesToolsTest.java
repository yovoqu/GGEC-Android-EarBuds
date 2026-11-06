package com.yovo.lib_ai.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.messages.MessagesCodec;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MessagesToolsTest {

    @Test
    public void encodesStrictExamplesDeferLoadingAndServerTools() throws Exception {
        JSONArray examples = new JSONArray().put(new JSONObject().put("city", "SZ"));
        YoMessageRequest request = YoMessageRequest.builder()
                .model("claude-sonnet-4-5")
                .maxTokens(512)
                .addUserMessage("hi")
                .addTool(YoMessageTool.function(
                        "get_weather",
                        "lookup",
                        new JSONObject().put("type", "object"),
                        true,
                        examples,
                        true))
                .addServerTool(YoMessageServerTool.of(
                        YoMessageServerTool.Kind.WEB_SEARCH,
                        new JSONObject().put("max_uses", 5)))
                .addServerTool(YoMessageServerTool.of(
                        YoMessageServerTool.Kind.WEB_FETCH,
                        new JSONObject().put("allowed_domains", new JSONArray().put("example.com"))))
                .build();

        JSONObject actual = MessagesCodec.encode(request, false);
        JSONObject expected = fixture("f10/request_server_tools.json");
        assertTrue("tool extensions must match: " + actual,
                expected.toString().equals(actual.toString()));

        JSONArray tools = actual.getJSONArray("tools");
        assertEquals(3, tools.length());
        JSONObject function = tools.getJSONObject(0);
        assertEquals(true, function.getBoolean("strict"));
        assertEquals(true, function.getBoolean("defer_loading"));
        assertEquals("SZ", function.getJSONArray("input_examples").getJSONObject(0).getString("city"));
        assertEquals("web_search_20250305", tools.getJSONObject(1).getString("type"));
        assertEquals("web_fetch_20250910", tools.getJSONObject(2).getString("type"));
        assertEquals(5, tools.getJSONObject(1).getInt("max_uses"));
    }

    @Test
    public void decodesServerToolUseAndResultKeepingRaw() throws Exception {
        YoMessage message = MessagesCodec.decode(fixture("f10/response_server_tools.json").toString());
        assertEquals(3, message.content().size());

        YoMessageContent use = message.content().get(0);
        assertEquals(YoMessageContent.Type.SERVER_TOOL_USE, use.type());
        assertEquals("srvtoolu_1", use.id());
        assertEquals("web_search", use.name());
        assertEquals("x", use.input().getString("query"));
        assertTrue(use.rawJson().contains("server_tool_use"));

        YoMessageContent result = message.content().get(1);
        assertEquals(YoMessageContent.Type.WEB_SEARCH_TOOL_RESULT, result.type());
        assertEquals("srvtoolu_1", result.id());
        assertTrue(result.rawJson().contains("web_search_result"));

        assertEquals("answer", message.content().get(2).text());
    }

    private static JSONObject fixture(String path) throws Exception {
        try (InputStream stream = MessagesToolsTest.class.getResourceAsStream("/fixtures/" + path)) {
            if (stream == null) {
                throw new IllegalStateException("missing fixture " + path);
            }
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
