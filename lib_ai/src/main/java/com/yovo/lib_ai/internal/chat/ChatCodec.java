package com.yovo.lib_ai.internal.chat;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.chat.YoChatChoice;
import com.yovo.lib_ai.chat.YoChatChunk;
import com.yovo.lib_ai.chat.YoChatCompletion;
import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatContentPart;
import com.yovo.lib_ai.chat.YoChatMessage;
import com.yovo.lib_ai.chat.YoChatResponseFormat;
import com.yovo.lib_ai.chat.YoChatTool;
import com.yovo.lib_ai.chat.YoChatToolCall;
import com.yovo.lib_ai.chat.YoChatToolChoice;
import com.yovo.lib_ai.chat.YoChatUsage;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.json.Jsons;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ChatCodec {

    private ChatCodec() {
    }

    public static JSONObject encode(YoChatCompletionRequest request, boolean stream) throws JSONException {
        JSONObject json = Jsons.copy(request.extraFields());
        json.put("model", request.model());
        JSONArray messages = new JSONArray();
        for (YoChatMessage message : request.messages()) {
            messages.put(encodeMessage(message));
        }
        json.put("messages", messages);
        Jsons.put(json, "temperature", request.temperature());
        Jsons.put(json, "top_p", request.topP());
        Jsons.put(json, "max_completion_tokens", request.maxCompletionTokens());
        if (!request.tools().isEmpty()) {
            JSONArray tools = new JSONArray();
            for (YoChatTool tool : request.tools()) {
                tools.put(encodeTool(tool));
            }
            json.put("tools", tools);
        }
        if (request.toolChoice() != null) {
            json.put("tool_choice", encodeToolChoice(request.toolChoice()));
        }
        if (request.responseFormat() != null) {
            json.put("response_format", encodeFormat(request.responseFormat()));
        }
        if (!request.stop().isEmpty()) {
            JSONArray stop = new JSONArray();
            for (String item : request.stop()) {
                stop.put(item);
            }
            json.put("stop", stop);
        }
        Jsons.put(json, "parallel_tool_calls", request.parallelToolCalls());
        Jsons.put(json, "reasoning_effort", request.reasoningEffort());
        if (stream) {
            json.put("stream", true);
            JSONObject streamOptions = json.optJSONObject("stream_options");
            if (streamOptions == null) {
                streamOptions = new JSONObject();
                json.put("stream_options", streamOptions);
            }
            if (!streamOptions.has("include_usage")) {
                streamOptions.put("include_usage", true);
            }
        } else {
            json.remove("stream");
        }
        return json;
    }

    public static YoChatCompletion decodeCompletion(String raw) {
        JSONObject json = Jsons.parseObject(raw);
        return new YoChatCompletion(
                Jsons.optString(json, "id"),
                Jsons.optString(json, "model"),
                decodeChoices(Jsons.optArray(json, "choices"), false),
                decodeUsage(Jsons.optObject(json, "usage")),
                json.toString());
    }

    public static YoChatChunk decodeChunk(String raw) {
        JSONObject json = Jsons.parseObject(raw);
        JSONObject error = Jsons.optObject(json, "error");
        if (error != null) {
            throw new YoAiProtocolException(Jsons.optString(error, "message"), json.toString());
        }
        return new YoChatChunk(
                Jsons.optString(json, "id"),
                Jsons.optString(json, "model"),
                decodeChoices(Jsons.optArray(json, "choices"), true),
                decodeUsage(Jsons.optObject(json, "usage")),
                json.toString());
    }

    private static JSONObject encodeMessage(YoChatMessage message) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("role", message.role());
        YoChatContentPart[] parts = message.parts();
        if (parts != null && parts.length > 0) {
            JSONArray content = new JSONArray();
            for (YoChatContentPart part : parts) {
                content.put(encodePart(part));
            }
            json.put("content", content);
        } else if (message.content() != null) {
            json.put("content", message.content());
        }
        if (message.toolCalls().length > 0) {
            JSONArray calls = new JSONArray();
            for (YoChatToolCall call : message.toolCalls()) {
                JSONObject function = new JSONObject();
                function.put("name", call.name());
                function.put("arguments", call.arguments());
                JSONObject encoded = new JSONObject();
                encoded.put("id", call.id());
                encoded.put("type", "function");
                encoded.put("function", function);
                calls.put(encoded);
            }
            json.put("tool_calls", calls);
        }
        Jsons.put(json, "tool_call_id", message.toolCallId());
        return json;
    }

    private static JSONObject encodePart(YoChatContentPart part) throws JSONException {
        if (part.raw() != null) {
            return part.raw();
        }
        JSONObject json = new JSONObject();
        json.put("type", "text");
        json.put("text", part.text());
        return json;
    }

    private static JSONObject encodeTool(YoChatTool tool) throws JSONException {
        JSONObject function = new JSONObject();
        function.put("name", tool.name());
        Jsons.put(function, "description", tool.description());
        function.put("parameters", tool.parameters());
        Jsons.put(function, "strict", tool.strict());
        JSONObject json = new JSONObject();
        json.put("type", "function");
        json.put("function", function);
        return json;
    }

    private static Object encodeToolChoice(YoChatToolChoice choice) throws JSONException {
        return switch (choice.kind()) {
            case AUTO -> "auto";
            case NONE -> "none";
            case REQUIRED -> "required";
            case FUNCTION -> {
                JSONObject function = new JSONObject();
                function.put("name", choice.functionName());
                JSONObject json = new JSONObject();
                json.put("type", "function");
                json.put("function", function);
                yield json;
            }
        };
    }

    private static JSONObject encodeFormat(YoChatResponseFormat format) throws JSONException {
        JSONObject json = new JSONObject();
        switch (format.kind()) {
            case TEXT -> json.put("type", "text");
            case JSON_OBJECT -> json.put("type", "json_object");
            case JSON_SCHEMA -> {
                json.put("type", "json_schema");
                JSONObject schema = new JSONObject();
                schema.put("name", format.schemaName());
                schema.put("schema", format.schema());
                schema.put("strict", format.strict());
                json.put("json_schema", schema);
            }
            default -> throw new YoAiProtocolException("Unsupported response format", null);
        }
        return json;
    }

    private static List<YoChatChoice> decodeChoices(@Nullable JSONArray choices, boolean delta) {
        List<YoChatChoice> decoded = new ArrayList<>();
        if (choices == null) {
            return decoded;
        }
        for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i);
            if (choice == null) {
                continue;
            }
            JSONObject message = Jsons.optObject(choice, delta ? "delta" : "message");
            decoded.add(new YoChatChoice(
                    choice.optInt("index", i),
                    message == null ? null : decodeMessage(message),
                    Jsons.optString(choice, "finish_reason")));
        }
        return decoded;
    }

    private static YoChatMessage decodeMessage(JSONObject message) {
        String role = Jsons.optString(message, "role");
        if (role == null) {
            role = "assistant";
        }
        YoChatMessage.Builder builder = YoChatMessage.builder().role(role);
        if (message.has("content") && !message.isNull("content")) {
            Object content = message.opt("content");
            if (content instanceof String text) {
                builder.content(text);
            } else if (content instanceof JSONArray parts) {
                builder.content(textFromParts(parts));
            } else {
                builder.content(String.valueOf(content));
            }
        }
        JSONArray toolCalls = Jsons.optArray(message, "tool_calls");
        if (toolCalls != null && toolCalls.length() > 0) {
            List<YoChatToolCall> calls = new ArrayList<>();
            for (int i = 0; i < toolCalls.length(); i++) {
                JSONObject call = toolCalls.optJSONObject(i);
                if (call == null) {
                    continue;
                }
                JSONObject function = Jsons.optObject(call, "function");
                String id = Jsons.optString(call, "id");
                String name = function == null ? null : Jsons.optString(function, "name");
                String arguments = function == null ? null : Jsons.optString(function, "arguments");
                int index = call.has("index") ? call.optInt("index") : i;
                calls.add(new YoChatToolCall(
                        index,
                        id == null ? "" : id,
                        name == null ? "" : name,
                        arguments == null ? "" : arguments));
            }
            if (!calls.isEmpty()) {
                builder.toolCalls(calls.toArray(YoChatToolCall[]::new));
            }
        }
        String toolCallId = Jsons.optString(message, "tool_call_id");
        if (toolCallId != null) {
            builder.toolCallId(toolCallId);
        }
        if (builderWouldBeEmpty(message, builder)) {
            builder.content("");
        }
        return builder.build();
    }

    private static boolean builderWouldBeEmpty(JSONObject message, YoChatMessage.Builder builder) {
        boolean hasContent = message.has("content") && !message.isNull("content");
        boolean hasTools = Jsons.optArray(message, "tool_calls") != null
                && Jsons.optArray(message, "tool_calls").length() > 0;
        return !hasContent && !hasTools;
    }

    private static String textFromParts(JSONArray parts) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part != null && "text".equals(Jsons.optString(part, "type"))) {
                String piece = Jsons.optString(part, "text");
                if (piece != null) {
                    text.append(piece);
                }
            }
        }
        return text.toString();
    }

    @Nullable
    private static YoChatUsage decodeUsage(@Nullable JSONObject usage) {
        if (usage == null) {
            return null;
        }
        return new YoChatUsage(
                usage.optInt("prompt_tokens", 0),
                usage.optInt("completion_tokens", 0),
                usage.optInt("total_tokens", 0));
    }
}
