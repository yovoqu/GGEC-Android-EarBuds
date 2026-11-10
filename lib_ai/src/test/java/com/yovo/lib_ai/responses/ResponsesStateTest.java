package com.yovo.lib_ai.responses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.responses.ResponsesCodec;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ResponsesStateTest {

    @Test
    public void encodesFullResponsesCreateFields() throws Exception {
        YoResponseRequest request = fullRequest();
        JSONObject actual = ResponsesCodec.encodeHttp(request, false);
        JSONObject expected = fixture("f08/request_full.json");
        assertTrue("full create body must match: " + actual,
                expected.toString().equals(actual.toString()));

        assertEquals(true, actual.getBoolean("background"));
        assertEquals("conv_1", actual.getString("conversation"));
        assertEquals(2, actual.getJSONArray("include").length());
        assertEquals("high", actual.getJSONObject("reasoning").getString("effort"));
        assertEquals("low", actual.getJSONObject("text").getString("verbosity"));
        assertEquals(3, actual.getInt("max_tool_calls"));
        assertEquals("flex", actual.getString("service_tier"));
        assertEquals("abc", actual.getJSONObject("metadata").getString("trace"));
    }

    @Test
    public void httpKeepsBackgroundWhileWebSocketDropsIt() throws Exception {
        YoResponseRequest request = fullRequest();
        JSONObject http = ResponsesCodec.encodeHttp(request, false);
        assertTrue(http.has("background"));
        assertEquals(true, http.getBoolean("background"));

        JSONObject socket = ResponsesCodec.encodeWebSocket(request, "main");
        assertFalse(socket.has("background"));
        assertFalse(socket.has("stream"));
        assertEquals("response.create", socket.getString("type"));
    }

    @Test
    public void decodesIncompleteReasonAndBackground() throws Exception {
        YoResponseObject response = ResponsesCodec.decodeResponse(fixture("f08/response_background.json").toString());
        assertEquals("incomplete", response.status());
        assertEquals(Boolean.TRUE, response.background());
        assertEquals("max_output_tokens", response.incompleteReason());
        assertEquals("part", response.text());
    }

    private static YoResponseRequest fullRequest() throws Exception {
        return YoResponseRequest.builder()
                .model("gpt-5")
                .input("hi")
                .background(true)
                .contextManagement(new JSONObject().put("type", "auto"))
                .conversation("conv_1")
                .include(java.util.List.of("web_search_call.action.sources", "message.output_text.logprobs"))
                .promptCacheOptions(new JSONObject().put("mode", "explicit"))
                .reasoning(new JSONObject().put("effort", "high").put("summary", "auto"))
                .textVerbosity("low")
                .truncation("auto")
                .maxToolCalls(3)
                .safetyIdentifier("user_hash")
                .serviceTier("flex")
                .metadata(new JSONObject().put("trace", "abc"))
                .build();
    }

    private static JSONObject fixture(String path) throws Exception {
        try (InputStream stream = ResponsesStateTest.class.getResourceAsStream("/fixtures/" + path)) {
            if (stream == null) {
                throw new IllegalStateException("missing fixture " + path);
            }
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
