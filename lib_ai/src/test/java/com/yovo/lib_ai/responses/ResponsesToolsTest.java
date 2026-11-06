package com.yovo.lib_ai.responses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.responses.ResponsesCodec;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ResponsesToolsTest {

    @Test
    public void encodesAllSevenServerTools() throws Exception {
        JSONObject fileSearch = new JSONObject()
                .put("vector_store_ids", new JSONArray().put("vs_1"))
                .put("max_num_results", 5);
        JSONObject codeInterpreter = new JSONObject()
                .put("container", new JSONObject().put("type", "auto"));
        JSONObject computer = new JSONObject()
                .put("display_width", 1280)
                .put("display_height", 720)
                .put("environment", "browser");
        JSONObject imageGeneration = new JSONObject().put("size", "1024x1024");
        JSONObject mcp = new JSONObject()
                .put("server_label", "deepwiki")
                .put("server_url", "https://mcp.example.com/sse")
                .put("require_approval", "never");

        YoResponseRequest request = YoResponseRequest.builder()
                .model("gpt-5")
                .input("hi")
                .addTool(YoResponseTool.server(
                        YoResponseTool.ServerTool.Kind.WEB_SEARCH,
                        new JSONObject().put("search_context_size", "medium")))
                .addTool(YoResponseTool.server(YoResponseTool.ServerTool.Kind.FILE_SEARCH, fileSearch))
                .addTool(YoResponseTool.server(YoResponseTool.ServerTool.Kind.CODE_INTERPRETER, codeInterpreter))
                .addTool(YoResponseTool.server(YoResponseTool.ServerTool.Kind.COMPUTER, computer))
                .addTool(YoResponseTool.server(YoResponseTool.ServerTool.Kind.IMAGE_GENERATION, imageGeneration))
                .addTool(YoResponseTool.server(YoResponseTool.ServerTool.Kind.MCP, mcp))
                .addTool(YoResponseTool.server(YoResponseTool.ServerTool.Kind.TOOL_SEARCH))
                .build();

        JSONObject actual = ResponsesCodec.encodeHttp(request, false);
        JSONObject expected = fixture("f07/request_tools.json");
        assertTrue("encoded tools must match official shape: " + actual,
                expected.toString().equals(actual.toString()));

        JSONArray tools = actual.getJSONArray("tools");
        assertEquals(7, tools.length());
        assertEquals("web_search", tools.getJSONObject(0).getString("type"));
        assertEquals("file_search", tools.getJSONObject(1).getString("type"));
        assertEquals("code_interpreter", tools.getJSONObject(2).getString("type"));
        assertEquals("computer", tools.getJSONObject(3).getString("type"));
        assertEquals("image_generation", tools.getJSONObject(4).getString("type"));
        assertEquals("mcp", tools.getJSONObject(5).getString("type"));
        assertEquals("tool_search", tools.getJSONObject(6).getString("type"));
    }

    @Test
    public void decodesServerSideCallItemsAndKeepsRaw() throws Exception {
        YoResponseObject response = ResponsesCodec.decodeResponse(fixture("f07/response_tools.json").toString());
        assertEquals(6, response.output().size());
        assertEquals(YoResponseOutputItem.Kind.WEB_SEARCH_CALL, response.output().get(0).kind());
        assertEquals(YoResponseOutputItem.Kind.FILE_SEARCH_CALL, response.output().get(1).kind());
        assertEquals(YoResponseOutputItem.Kind.CODE_INTERPRETER_CALL, response.output().get(2).kind());
        assertEquals(YoResponseOutputItem.Kind.COMPUTER_CALL, response.output().get(3).kind());
        assertEquals(YoResponseOutputItem.Kind.MCP_CALL, response.output().get(4).kind());
        assertEquals(YoResponseOutputItem.Kind.OTHER, response.output().get(5).kind());
        assertEquals("web_search_call", response.output().get(0).type());
        assertEquals("call_c", response.output().get(3).callId());
        assertEquals("query", response.output().get(4).name());
        assertTrue(response.output().get(5).rawJson().contains("unknown_future_call"));
    }

    @Test
    public void unknownServerToolKindStaysOutOfFunctionShape() throws Exception {
        YoResponseRequest request = YoResponseRequest.builder()
                .model("gpt-5")
                .input("hi")
                .addTool(YoResponseTool.server(YoResponseTool.ServerTool.Kind.TOOL_SEARCH))
                .build();
        JSONObject tool = ResponsesCodec.encodeHttp(request, false)
                .getJSONArray("tools")
                .getJSONObject(0);
        assertEquals("tool_search", tool.getString("type"));
        assertTrue(!tool.has("name"));
    }

    private static JSONObject fixture(String path) throws Exception {
        try (InputStream stream = ResponsesToolsTest.class.getResourceAsStream("/fixtures/" + path)) {
            if (stream == null) {
                throw new IllegalStateException("missing fixture " + path);
            }
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
