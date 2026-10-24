package com.yovo.lib_ai.internal.responses;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.internal.json.Jsons;
import com.yovo.lib_ai.internal.protocol.UsageFields;
import com.yovo.lib_ai.responses.YoResponseContentPart;
import com.yovo.lib_ai.responses.YoResponseInput;
import com.yovo.lib_ai.responses.YoResponseObject;
import com.yovo.lib_ai.responses.YoResponseOutputItem;
import com.yovo.lib_ai.responses.YoResponseRequest;
import com.yovo.lib_ai.responses.YoResponseStreamEvent;
import com.yovo.lib_ai.responses.YoResponseTextFormat;
import com.yovo.lib_ai.responses.YoResponseTool;
import com.yovo.lib_ai.responses.YoResponseToolChoice;
import com.yovo.lib_ai.responses.YoResponseUsage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ResponsesCodec {

    private ResponsesCodec() {
    }

    public static JSONObject encodeHttp(YoResponseRequest request, boolean stream) throws JSONException {
        JSONObject json = encode(request, stream);
        json.remove("generate");
        return json;
    }

    public static JSONObject encodeWebSocket(YoResponseRequest request, @Nullable String streamId) throws JSONException {
        JSONObject json = encode(request, false);
        json.put("type", ResponsesEvents.CREATE);
        json.remove("stream");
        json.remove("background");
        if (streamId != null) {
            json.put("stream_id", streamId);
        }
        if (request.generate() != null) {
            json.put("generate", request.generate());
        }
        return json;
    }

    public static JSONObject encodeSteer(String previousResponseId, Object input) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", ResponsesEvents.STEER);
        json.put("previous_response_id", previousResponseId);
        json.put("input", input);
        return json;
    }

    public static Object encodeInputValue(YoResponseRequest request) throws JSONException {
        if (request.inputText() != null) {
            return request.inputText();
        }
        return encodeItems(request.inputItems());
    }

    public static JSONArray encodeItems(List<YoResponseInput> items) throws JSONException {
        JSONArray array = new JSONArray();
        for (YoResponseInput item : items) {
            array.put(encodeItem(item));
        }
        return array;
    }

    public static YoResponseObject decodeResponse(String raw) {
        return decodeResponse(Jsons.parseObject(raw));
    }

    public static YoResponseObject decodeResponse(JSONObject json) {
        JSONArray output = Jsons.optArray(json, "output");
        List<YoResponseOutputItem> items = new ArrayList<>();
        if (output != null) {
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item != null) {
                    items.add(decodeItem(item));
                }
            }
        }
        return new YoResponseObject(
                Jsons.optString(json, "id"),
                Jsons.optString(json, "status"),
                Jsons.optString(json, "model"),
                items,
                decodeUsage(Jsons.optObject(json, "usage")),
                json.toString());
    }

    public static YoResponseStreamEvent decodeEvent(JSONObject json) {
        return new YoResponseStreamEvent(
                json.optString("type", ""),
                Jsons.optString(json, "stream_id"),
                Jsons.optString(json, "delta"),
                json.optInt("output_index", -1),
                json.toString());
    }

    private static JSONObject encode(YoResponseRequest request, boolean stream) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", request.model());
        json.put("input", encodeInputValue(request));
        Jsons.put(json, "instructions", request.instructions());
        Jsons.put(json, "previous_response_id", request.previousResponseId());
        if (!request.tools().isEmpty()) {
            JSONArray tools = new JSONArray();
            for (YoResponseTool tool : request.tools()) {
                tools.put(encodeTool(tool));
            }
            json.put("tools", tools);
        }
        if (request.toolChoice() != null) {
            json.put("tool_choice", encodeToolChoice(request.toolChoice()));
        }
        Jsons.put(json, "parallel_tool_calls", request.parallelToolCalls());
        Jsons.put(json, "max_output_tokens", request.maxOutputTokens());
        Jsons.put(json, "temperature", request.temperature());
        Jsons.put(json, "top_p", request.topP());
        Jsons.put(json, "store", request.store());
        if (request.textFormat() != null) {
            json.put("text", encodeText(request.textFormat()));
        }
        if (stream) {
            json.put("stream", true);
        } else {
            json.remove("stream");
        }
        json.remove("background");
        return json;
    }

    private static JSONObject encodeItem(YoResponseInput item) throws JSONException {
        JSONObject json = new JSONObject();
        switch (item.type()) {
            case MESSAGE -> {
                json.put("type", "message");
                json.put("role", item.role());
                JSONArray content = new JSONArray();
                if (!item.parts().isEmpty()) {
                    for (YoResponseContentPart part : item.parts()) {
                        content.put(encodeContentPart(part));
                    }
                } else {
                    // 助手历史是模型已经说过的话，协议要求标成 output_text，input_text 会被官方接口拒绝。
                    JSONObject text = new JSONObject();
                    text.put("type", "assistant".equals(item.role()) ? "output_text" : "input_text");
                    text.put("text", item.text());
                    content.put(text);
                }
                json.put("content", content);
            }
            case FUNCTION_CALL -> {
                json.put("type", "function_call");
                json.put("call_id", item.callId());
                json.put("name", item.name());
                json.put("arguments", item.arguments());
            }
            case FUNCTION_CALL_OUTPUT -> {
                json.put("type", "function_call_output");
                json.put("call_id", item.callId());
                json.put("output", item.output());
            }
            default -> throw new JSONException("Unsupported input type");
        }
        return json;
    }

    private static JSONObject encodeContentPart(YoResponseContentPart part) throws JSONException {
        JSONObject json = new JSONObject();
        switch (part.kind()) {
            case INPUT_TEXT -> {
                json.put("type", "input_text");
                json.put("text", part.text());
            }
            case INPUT_IMAGE -> {
                json.put("type", "input_image");
                if (part.fileId() != null) {
                    json.put("file_id", part.fileId());
                } else {
                    json.put("image_url", part.imageUrl());
                    if (part.detail() != null) {
                        json.put("detail", part.detail());
                    }
                }
            }
            case INPUT_FILE -> {
                json.put("type", "input_file");
                if (part.fileId() != null) {
                    json.put("file_id", part.fileId());
                } else if (part.fileData() != null) {
                    json.put("file_data", part.fileData());
                    json.put("filename", part.filename());
                } else {
                    json.put("file_url", part.fileUrl());
                }
                if (part.detail() != null) {
                    json.put("detail", part.detail());
                }
            }
            default -> throw new JSONException("Unsupported content part");
        }
        return json;
    }

    private static JSONObject encodeTool(YoResponseTool tool) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", "function");
        json.put("name", tool.name());
        Jsons.put(json, "description", tool.description());
        json.put("parameters", tool.parameters());
        Jsons.put(json, "strict", tool.strict());
        return json;
    }

    private static Object encodeToolChoice(YoResponseToolChoice choice) throws JSONException {
        return switch (choice.kind()) {
            case AUTO -> "auto";
            case NONE -> "none";
            case REQUIRED -> "required";
            case FUNCTION -> {
                JSONObject json = new JSONObject();
                json.put("type", "function");
                json.put("name", choice.functionName());
                yield json;
            }
        };
    }

    private static JSONObject encodeText(YoResponseTextFormat format) throws JSONException {
        JSONObject encoded = new JSONObject();
        JSONObject inner = new JSONObject();
        switch (format.kind()) {
            case TEXT -> inner.put("type", "text");
            case JSON_OBJECT -> inner.put("type", "json_object");
            case JSON_SCHEMA -> {
                inner.put("type", "json_schema");
                inner.put("name", format.name());
                inner.put("strict", format.strict());
                inner.put("schema", format.schema());
            }
            default -> throw new JSONException("Unsupported text format");
        }
        encoded.put("format", inner);
        return encoded;
    }

    private static YoResponseOutputItem decodeItem(JSONObject item) {
        String type = item.optString("type", "");
        String text = null;
        JSONArray content = Jsons.optArray(item, "content");
        if (content != null) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject part = content.optJSONObject(i);
                if (part != null && "output_text".equals(part.optString("type"))) {
                    String piece = Jsons.optString(part, "text");
                    if (piece != null) {
                        builder.append(piece);
                    }
                }
            }
            text = builder.toString();
        }
        return new YoResponseOutputItem(
                type,
                Jsons.optString(item, "id"),
                Jsons.optString(item, "role"),
                Jsons.optString(item, "status"),
                text,
                Jsons.optString(item, "name"),
                Jsons.optString(item, "call_id"),
                Jsons.optString(item, "arguments"),
                item.toString());
    }

    /**
     * Responses 的用量。协议自带合计就照抄，没有（0）时才自己相加；
     * 缓存命中的令牌在 {@code input_tokens_details} 里，单独取出来。
     */
    @Nullable
    private static YoResponseUsage decodeUsage(@Nullable JSONObject usage) {
        if (usage == null) {
            return null;
        }
        int input = UsageFields.intOrZero(usage, "input_tokens");
        int output = UsageFields.intOrZero(usage, "output_tokens");
        int total = UsageFields.intOrZero(usage, "total_tokens");
        return new YoResponseUsage(input, output, total,
                UsageFields.cachedTokens(usage),
                UsageFields.cacheCreationTokens(usage),
                UsageFields.cost(usage));
    }
}
