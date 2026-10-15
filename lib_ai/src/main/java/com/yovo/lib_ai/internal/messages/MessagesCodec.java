package com.yovo.lib_ai.internal.messages;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.internal.json.Jsons;
import com.yovo.lib_ai.messages.YoMessage;
import com.yovo.lib_ai.messages.YoMessageContent;
import com.yovo.lib_ai.messages.YoMessageRequest;
import com.yovo.lib_ai.messages.YoMessageStreamEvent;
import com.yovo.lib_ai.messages.YoMessageTool;
import com.yovo.lib_ai.messages.YoMessageToolChoice;
import com.yovo.lib_ai.messages.YoMessageTurn;
import com.yovo.lib_ai.messages.YoMessageUsage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class MessagesCodec {

    private MessagesCodec() {
    }

    public static JSONObject encode(YoMessageRequest request, boolean stream) throws JSONException {
        JSONObject json = Jsons.copy(request.extraFields());
        json.put("model", request.model());
        json.put("max_tokens", request.maxTokens());
        JSONArray messages = new JSONArray();
        for (YoMessageTurn turn : request.messages()) {
            messages.put(encodeTurn(turn));
        }
        json.put("messages", messages);
        Jsons.put(json, "system", request.system());
        if (!request.tools().isEmpty()) {
            JSONArray tools = new JSONArray();
            for (YoMessageTool tool : request.tools()) {
                JSONObject encoded = new JSONObject();
                encoded.put("name", tool.name());
                Jsons.put(encoded, "description", tool.description());
                encoded.put("input_schema", tool.inputSchema());
                tools.put(encoded);
            }
            json.put("tools", tools);
        }
        if (request.toolChoice() != null) {
            json.put("tool_choice", encodeToolChoice(request.toolChoice()));
        }
        Jsons.put(json, "temperature", request.temperature());
        Jsons.put(json, "top_p", request.topP());
        if (!request.stopSequences().isEmpty()) {
            JSONArray stop = new JSONArray();
            for (String item : request.stopSequences()) {
                stop.put(item);
            }
            json.put("stop_sequences", stop);
        }
        if (request.userId() != null) {
            JSONObject metadata = Jsons.optObject(json, "metadata");
            if (metadata == null) {
                metadata = new JSONObject();
            }
            metadata.put("user_id", request.userId());
            json.put("metadata", metadata);
        }
        if (stream) {
            json.put("stream", true);
        } else {
            json.remove("stream");
        }
        return json;
    }

    public static YoMessage decode(String raw) {
        return decode(Jsons.parseObject(raw));
    }

    public static YoMessage decode(JSONObject json) {
        JSONArray content = Jsons.optArray(json, "content");
        List<YoMessageContent> blocks = new ArrayList<>();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                if (block != null) {
                    blocks.add(decodeBlock(block));
                }
            }
        }
        return new YoMessage(
                Jsons.optString(json, "id"),
                Jsons.optString(json, "model"),
                Jsons.optString(json, "stop_reason"),
                blocks,
                decodeUsage(Jsons.optObject(json, "usage")),
                json.toString());
    }

    public static YoMessageStreamEvent decodeEvent(JSONObject event) {
        String type = event.optString("type", "");
        JSONObject delta = Jsons.optObject(event, "delta");
        String text = null;
        if (delta != null) {
            text = Jsons.optString(delta, "text");
            if (text == null) {
                text = Jsons.optString(delta, "thinking");
            }
            if (text == null) {
                text = Jsons.optString(delta, "partial_json");
            }
        }
        return new YoMessageStreamEvent(type, event.optInt("index", -1), text, event.toString());
    }

    private static JSONObject encodeTurn(YoMessageTurn turn) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("role", turn.role());
        if (turn.text() != null) {
            json.put("content", turn.text());
            return json;
        }
        JSONArray content = new JSONArray();
        for (YoMessageContent block : turn.blocks()) {
            content.put(encodeBlock(block));
        }
        json.put("content", content);
        return json;
    }

    private static JSONObject encodeBlock(YoMessageContent block) throws JSONException {
        if (block.type() == YoMessageContent.Type.RAW) {
            return block.raw();
        }
        JSONObject json = new JSONObject();
        switch (block.type()) {
            case TEXT -> {
                json.put("type", "text");
                json.put("text", block.text());
            }
            case TOOL_USE -> {
                json.put("type", "tool_use");
                json.put("id", block.id());
                json.put("name", block.name());
                json.put("input", block.input());
            }
            case TOOL_RESULT -> {
                json.put("type", "tool_result");
                json.put("tool_use_id", block.id());
                json.put("content", block.text());
            }
            case THINKING -> {
                json.put("type", "thinking");
                json.put("thinking", block.text());
                if (block.signature() != null) {
                    json.put("signature", block.signature());
                }
            }
            case RAW -> {
                return block.raw();
            }
            default -> throw new JSONException("Unsupported content block");
        }
        return json;
    }

    private static JSONObject encodeToolChoice(YoMessageToolChoice choice) throws JSONException {
        JSONObject json = new JSONObject();
        switch (choice.kind()) {
            case AUTO -> json.put("type", "auto");
            case ANY -> json.put("type", "any");
            case NONE -> json.put("type", "none");
            case TOOL -> {
                json.put("type", "tool");
                json.put("name", choice.name());
            }
            default -> throw new JSONException("Unsupported tool choice");
        }
        return json;
    }

    private static YoMessageContent decodeBlock(JSONObject block) {
        String type = block.optString("type", "");
        return switch (type) {
            case "text" -> YoMessageContent.text(block.optString("text", ""));
            case "tool_use" -> {
                String id = block.optString("id", "");
                String name = block.optString("name", "");
                if (id.isBlank() || name.isBlank()) {
                    yield YoMessageContent.raw(block);
                }
                JSONObject input = Jsons.optObject(block, "input");
                yield YoMessageContent.toolUse(id, name, input == null ? new JSONObject() : input);
            }
            case "tool_result" -> {
                String toolUseId = block.optString("tool_use_id", "");
                if (toolUseId.isBlank()) {
                    yield YoMessageContent.raw(block);
                }
                yield YoMessageContent.toolResult(toolUseId, block.optString("content", ""));
            }
            case "thinking" -> YoMessageContent.thinking(
                    block.optString("thinking", ""),
                    Jsons.optString(block, "signature"));
            default -> YoMessageContent.raw(block);
        };
    }

    @Nullable
    private static YoMessageUsage decodeUsage(@Nullable JSONObject usage) {
        if (usage == null) {
            return null;
        }
        return new YoMessageUsage(usage.optInt("input_tokens", 0), usage.optInt("output_tokens", 0));
    }
}
