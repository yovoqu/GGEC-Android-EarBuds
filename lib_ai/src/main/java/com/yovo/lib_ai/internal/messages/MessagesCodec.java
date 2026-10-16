package com.yovo.lib_ai.internal.messages;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
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
        JSONObject json = new JSONObject();
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
        Jsons.put(json, "top_k", request.topK());
        if (!request.stopSequences().isEmpty()) {
            JSONArray stop = new JSONArray();
            for (String item : request.stopSequences()) {
                stop.put(item);
            }
            json.put("stop_sequences", stop);
        }
        if (request.userId() != null) {
            JSONObject metadata = new JSONObject();
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
        JSONObject json = new JSONObject();
        switch (block.type()) {
            case TEXT -> {
                json.put("type", "text");
                json.put("text", block.text());
            }
            case IMAGE -> {
                json.put("type", "image");
                JSONObject source = new JSONObject();
                source.put("type", block.sourceType());
                if ("base64".equals(block.sourceType())) {
                    source.put("media_type", block.mediaType());
                    source.put("data", block.payload());
                } else if ("file".equals(block.sourceType())) {
                    source.put("file_id", block.payload());
                } else {
                    source.put("url", block.payload());
                }
                json.put("source", source);
            }
            case DOCUMENT -> {
                json.put("type", "document");
                json.put("source", encodeSource(block));
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
            case REDACTED_THINKING -> {
                json.put("type", "redacted_thinking");
                json.put("data", block.payload());
            }
            default -> throw new JSONException("Unsupported content block");
        }
        return json;
    }

    private static JSONObject encodeSource(YoMessageContent block) throws JSONException {
        JSONObject source = new JSONObject();
        source.put("type", block.sourceType());
        if ("base64".equals(block.sourceType()) || "text".equals(block.sourceType())) {
            source.put("media_type", block.mediaType());
            source.put("data", block.payload());
        } else if ("file".equals(block.sourceType())) {
            source.put("file_id", block.payload());
        } else {
            source.put("url", block.payload());
        }
        return source;
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
            case "image" -> decodeSource(block, true);
            case "document" -> decodeSource(block, false);
            case "tool_use" -> {
                String id = block.optString("id", "");
                String name = block.optString("name", "");
                if (id.isBlank() || name.isBlank()) {
                    throw new YoAiProtocolException("tool_use requires id and name", block.toString());
                }
                JSONObject input = Jsons.optObject(block, "input");
                yield YoMessageContent.toolUse(id, name, input == null ? new JSONObject() : input);
            }
            case "tool_result" -> {
                String toolUseId = block.optString("tool_use_id", "");
                if (toolUseId.isBlank()) {
                    throw new YoAiProtocolException("tool_result requires tool_use_id", block.toString());
                }
                yield YoMessageContent.toolResult(toolUseId, block.optString("content", ""));
            }
            case "thinking" -> YoMessageContent.thinking(
                    block.optString("thinking", ""),
                    Jsons.optString(block, "signature"));
            case "redacted_thinking" -> YoMessageContent.redactedThinking(block.optString("data", ""));
            default -> throw new YoAiProtocolException("Unsupported content block: " + type, block.toString());
        };
    }

    private static YoMessageContent decodeSource(JSONObject block, boolean image) {
        JSONObject source = Jsons.optObject(block, "source");
        if (source == null) {
            throw new YoAiProtocolException("content block requires source", block.toString());
        }
        String sourceType = source.optString("type", "");
        try {
            return switch (sourceType) {
                case "url" -> image
                        ? YoMessageContent.imageUrl(source.optString("url", ""))
                        : YoMessageContent.documentUrl(source.optString("url", ""));
                case "base64" -> image
                        ? YoMessageContent.imageBase64(source.optString("media_type", ""), source.optString("data", ""))
                        : YoMessageContent.documentBase64(source.optString("media_type", ""), source.optString("data", ""));
                case "file" -> image
                        ? YoMessageContent.imageFile(source.optString("file_id", ""))
                        : YoMessageContent.documentFile(source.optString("file_id", ""));
                case "text" -> {
                    if (image) {
                        throw new YoAiProtocolException("image source does not accept text", block.toString());
                    }
                    yield YoMessageContent.documentText(source.optString("data", ""));
                }
                default -> throw new YoAiProtocolException("Unsupported source type: " + sourceType, block.toString());
            };
        } catch (IllegalArgumentException exception) {
            throw new YoAiProtocolException(exception.getMessage(), block.toString(), exception);
        }
    }

    @Nullable
    private static YoMessageUsage decodeUsage(@Nullable JSONObject usage) {
        if (usage == null) {
            return null;
        }
        return new YoMessageUsage(usage.optInt("input_tokens", 0), usage.optInt("output_tokens", 0));
    }
}
