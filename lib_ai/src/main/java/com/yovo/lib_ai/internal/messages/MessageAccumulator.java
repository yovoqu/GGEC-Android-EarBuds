package com.yovo.lib_ai.internal.messages;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.json.Jsons;
import com.yovo.lib_ai.messages.YoMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class MessageAccumulator {

    private JSONObject message = new JSONObject();
    private final JSONArray content = new JSONArray();
    private final Map<Integer, StringBuilder> partialJson = new HashMap<>();
    private boolean stop;
    private boolean error;
    private String errorMessage;

    public void apply(JSONObject event) throws JSONException {
        String type = event.optString("type");
        switch (type) {
            case "message_start" -> {
                JSONObject started = Jsons.optObject(event, "message");
                if (started != null) {
                    message = Jsons.copy(started);
                    message.put("content", new JSONArray());
                }
            }
            case "content_block_start" -> putBlock(event.optInt("index", content.length()), Jsons.optObject(event, "content_block"));
            case "content_block_delta" -> applyDelta(event);
            case "message_delta" -> applyMessageDelta(event);
            case "message_stop" -> {
                finishToolInput();
                stop = true;
            }
            case "error" -> {
                error = true;
                errorMessage = errorMessage(event);
            }
            case "ping", "content_block_stop" -> {
                // Heartbeats and block boundaries do not change the accumulated message.
            }
            default -> {
                // Keep unknown events on the listener payload only.
            }
        }
        message.put("content", copyContent());
    }

    public boolean isStop() {
        return stop;
    }

    public boolean isError() {
        return error;
    }

    public String errorMessage() {
        return errorMessage == null ? "Anthropic stream error" : errorMessage;
    }

    public YoMessage toMessage() {
        try {
            message.put("content", copyContent());
        } catch (JSONException exception) {
            throw new YoAiProtocolException(exception.getMessage(), message.toString(), exception);
        }
        return MessagesCodec.decode(message);
    }

    private void applyDelta(JSONObject event) throws JSONException {
        JSONObject delta = Jsons.optObject(event, "delta");
        if (delta == null) {
            return;
        }
        int index = event.optInt("index", 0);
        String deltaType = delta.optString("type");
        JSONObject block = ensureBlock(index);
        switch (deltaType) {
            case "text_delta" -> block.put("text", block.optString("text", "") + delta.optString("text", ""));
            case "thinking_delta" -> block.put(
                    "thinking",
                    block.optString("thinking", "") + delta.optString("thinking", ""));
            case "input_json_delta" -> partialJson
                    .computeIfAbsent(index, ignored -> new StringBuilder())
                    .append(delta.optString("partial_json", ""));
            case "signature_delta" -> block.put(
                    "signature",
                    block.optString("signature", "") + delta.optString("signature", ""));
            default -> {
                // 未识别的增量仍留在监听器收到的原始事件里。
            }
        }
    }

    private void applyMessageDelta(JSONObject event) throws JSONException {
        JSONObject delta = Jsons.optObject(event, "delta");
        if (delta != null) {
            if (delta.has("stop_reason")) {
                message.put("stop_reason", delta.get("stop_reason"));
            }
            if (delta.has("stop_sequence")) {
                message.put("stop_sequence", delta.get("stop_sequence"));
            }
        }
        JSONObject usage = Jsons.optObject(event, "usage");
        if (usage != null) {
            JSONObject current = Jsons.optObject(message, "usage");
            if (current == null) {
                current = new JSONObject();
            }
            if (usage.has("output_tokens")) {
                current.put("output_tokens", usage.get("output_tokens"));
            }
            if (usage.has("input_tokens")) {
                current.put("input_tokens", usage.get("input_tokens"));
            }
            message.put("usage", current);
        }
    }

    private void finishToolInput() throws JSONException {
        for (Map.Entry<Integer, StringBuilder> entry : partialJson.entrySet()) {
            JSONObject block = content.optJSONObject(entry.getKey());
            if (block == null || !"tool_use".equals(block.optString("type"))) {
                continue;
            }
            String raw = entry.getValue().toString();
            if (raw.isBlank()) {
                continue;
            }
            try {
                block.put("input", new JSONObject(raw));
            } catch (JSONException exception) {
                block.put("input_json", raw);
            }
        }
    }

    private void putBlock(int index, JSONObject block) throws JSONException {
        while (content.length() <= index) {
            content.put(new JSONObject());
        }
        content.put(index, block == null ? new JSONObject() : Jsons.copy(block));
    }

    private JSONObject ensureBlock(int index) throws JSONException {
        while (content.length() <= index) {
            content.put(new JSONObject());
        }
        JSONObject block = content.optJSONObject(index);
        if (block == null) {
            block = new JSONObject();
            content.put(index, block);
        }
        return block;
    }

    private JSONArray copyContent() throws JSONException {
        JSONArray copy = new JSONArray();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block != null && block.length() > 0) {
                copy.put(Jsons.copy(block));
            }
        }
        return copy;
    }

    private static String errorMessage(JSONObject event) {
        JSONObject error = Jsons.optObject(event, "error");
        if (error != null && Jsons.optString(error, "message") != null) {
            return Jsons.optString(error, "message");
        }
        return "Anthropic stream error";
    }
}
