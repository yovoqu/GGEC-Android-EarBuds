package com.yovo.lib_ai.internal.responses;

import com.yovo.lib_ai.internal.json.Jsons;
import com.yovo.lib_ai.responses.YoResponseObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 把 Responses 事件折成和一次性调用相同的对象。
 * 条目的 type 在创建时定死：文本增量不改 function_call，参数增量不改 message。
 * 服务端快照只有带上非空 output 时才替换已折叠的条目。
 */
public final class ResponsesReducer {

    private JSONObject response = new JSONObject();
    private JSONArray output = new JSONArray();

    public void apply(JSONObject event) throws JSONException {
        String type = event.optString("type");
        JSONObject snapshot = Jsons.optObject(event, "response");
        if (snapshot != null && isResponseSnapshot(type)) {
            response = Jsons.copy(snapshot);
            JSONArray snapshotOutput = Jsons.optArray(snapshot, "output");
            if (snapshotOutput != null && snapshotOutput.length() > 0) {
                replaceOutput(snapshotOutput);
            }
        }
        switch (type) {
            case ResponsesEvents.OUTPUT_ITEM_ADDED, ResponsesEvents.OUTPUT_ITEM_DONE -> {
                JSONObject item = Jsons.optObject(event, "item");
                if (item != null) {
                    putOutput(event.optInt("output_index", output.length()), Jsons.copy(item));
                }
            }
            case ResponsesEvents.CONTENT_PART_ADDED -> {
                JSONObject part = Jsons.optObject(event, "part");
                if (part != null) {
                    putPart(event.optInt("output_index", 0), event.optInt("content_index", 0), Jsons.copy(part));
                }
            }
            case ResponsesEvents.OUTPUT_TEXT_DELTA -> appendText(
                    event.optInt("output_index", 0),
                    event.optInt("content_index", 0),
                    event.optString("delta", ""));
            case ResponsesEvents.OUTPUT_TEXT_DONE -> {
                if (event.has("text")) {
                    setText(
                            event.optInt("output_index", 0),
                            event.optInt("content_index", 0),
                            event.optString("text", ""));
                }
            }
            case ResponsesEvents.FUNCTION_CALL_ARGUMENTS_DELTA -> appendArguments(
                    event.optInt("output_index", 0),
                    event.optString("delta", ""));
            case ResponsesEvents.FUNCTION_CALL_ARGUMENTS_DONE -> {
                if (event.has("arguments")) {
                    setArguments(event.optInt("output_index", 0), event.optString("arguments", ""));
                }
            }
            case ResponsesEvents.REASONING_SUMMARY_TEXT_DELTA -> appendSummary(
                    event.optInt("output_index", 0),
                    event.optInt("summary_index", 0),
                    event.optString("delta", ""));
            case ResponsesEvents.REASONING_SUMMARY_TEXT_DONE -> {
                if (event.has("text")) {
                    setSummary(
                            event.optInt("output_index", 0),
                            event.optInt("summary_index", 0),
                            event.optString("text", ""));
                }
            }
            case ResponsesEvents.REFUSAL_DELTA -> appendRefusal(
                    event.optInt("output_index", 0),
                    event.optInt("content_index", 0),
                    event.optString("delta", ""));
            case ResponsesEvents.REFUSAL_DONE -> {
                if (event.has("refusal")) {
                    setRefusal(
                            event.optInt("output_index", 0),
                            event.optInt("content_index", 0),
                            event.optString("refusal", ""));
                }
            }
            default -> {
                // 未识别的事件仍留在监听器收到的原始 JSON 里。
            }
        }
        writeOutput();
    }

    public YoResponseObject toObject() {
        writeOutput();
        return ResponsesCodec.decodeResponse(response);
    }

    private static boolean isResponseSnapshot(String type) {
        return ResponsesEvents.CREATED.equals(type)
                || ResponsesEvents.IN_PROGRESS.equals(type)
                || ResponsesEvents.COMPLETED.equals(type)
                || ResponsesEvents.FAILED.equals(type)
                || ResponsesEvents.INCOMPLETE.equals(type);
    }

    private void replaceOutput(JSONArray snapshotOutput) throws JSONException {
        JSONArray replacement = new JSONArray();
        for (int i = 0; i < snapshotOutput.length(); i++) {
            JSONObject item = snapshotOutput.optJSONObject(i);
            replacement.put(item == null ? new JSONObject() : Jsons.copy(item));
        }
        output = replacement;
    }

    private void putOutput(int index, JSONObject item) throws JSONException {
        while (output.length() <= index) {
            output.put(new JSONObject());
        }
        output.put(index, item);
    }

    private JSONObject slot(int index) throws JSONException {
        while (output.length() <= index) {
            output.put(new JSONObject());
        }
        JSONObject item = output.optJSONObject(index);
        if (item == null) {
            item = new JSONObject();
            output.put(index, item);
        }
        return item;
    }

    private JSONObject typedItem(int index, String type) throws JSONException {
        JSONObject item = slot(index);
        if (item.length() == 0) {
            item.put("type", type);
            return item;
        }
        if (!type.equals(item.optString("type"))) {
            return null;
        }
        return item;
    }

    private void appendText(int outputIndex, int contentIndex, String delta) throws JSONException {
        JSONObject part = textPart(outputIndex, contentIndex);
        if (part == null) {
            return;
        }
        part.put("text", part.optString("text", "") + delta);
    }

    private void setText(int outputIndex, int contentIndex, String text) throws JSONException {
        JSONObject part = textPart(outputIndex, contentIndex);
        if (part != null) {
            part.put("text", text);
        }
    }

    private JSONObject textPart(int outputIndex, int contentIndex) throws JSONException {
        JSONObject item = typedItem(outputIndex, ResponsesEvents.ITEM_MESSAGE);
        if (item == null) {
            return null;
        }
        if (!item.has("role")) {
            item.put("role", "assistant");
        }
        return contentPart(item, contentIndex, ResponsesEvents.PART_OUTPUT_TEXT, "text");
    }

    private void appendArguments(int outputIndex, String delta) throws JSONException {
        JSONObject item = functionItem(outputIndex);
        if (item == null) {
            return;
        }
        item.put("arguments", item.optString("arguments", "") + delta);
    }

    private void setArguments(int outputIndex, String arguments) throws JSONException {
        JSONObject item = functionItem(outputIndex);
        if (item != null) {
            item.put("arguments", arguments);
        }
    }

    private JSONObject functionItem(int outputIndex) throws JSONException {
        JSONObject item = typedItem(outputIndex, ResponsesEvents.ITEM_FUNCTION_CALL);
        if (item == null) {
            return null;
        }
        if (!item.has("arguments")) {
            item.put("arguments", "");
        }
        return item;
    }

    private void appendSummary(int outputIndex, int summaryIndex, String delta) throws JSONException {
        JSONObject part = summaryPart(outputIndex, summaryIndex);
        if (part == null) {
            return;
        }
        part.put("text", part.optString("text", "") + delta);
    }

    private void setSummary(int outputIndex, int summaryIndex, String text) throws JSONException {
        JSONObject part = summaryPart(outputIndex, summaryIndex);
        if (part != null) {
            part.put("text", text);
        }
    }

    private JSONObject summaryPart(int outputIndex, int summaryIndex) throws JSONException {
        JSONObject item = typedItem(outputIndex, ResponsesEvents.ITEM_REASONING);
        if (item == null) {
            return null;
        }
        JSONArray summary = Jsons.optArray(item, "summary");
        if (summary == null) {
            summary = new JSONArray();
            item.put("summary", summary);
        }
        while (summary.length() <= summaryIndex) {
            summary.put(new JSONObject());
        }
        JSONObject part = summary.optJSONObject(summaryIndex);
        if (part == null || part.length() == 0) {
            part = new JSONObject();
            part.put("type", ResponsesEvents.PART_SUMMARY_TEXT);
            part.put("text", "");
            summary.put(summaryIndex, part);
        }
        return part;
    }

    private void appendRefusal(int outputIndex, int contentIndex, String delta) throws JSONException {
        JSONObject part = refusalPart(outputIndex, contentIndex);
        if (part == null) {
            return;
        }
        part.put("refusal", part.optString("refusal", "") + delta);
    }

    private void setRefusal(int outputIndex, int contentIndex, String refusal) throws JSONException {
        JSONObject part = refusalPart(outputIndex, contentIndex);
        if (part != null) {
            part.put("refusal", refusal);
        }
    }

    private JSONObject refusalPart(int outputIndex, int contentIndex) throws JSONException {
        JSONObject item = typedItem(outputIndex, ResponsesEvents.ITEM_MESSAGE);
        if (item == null) {
            return null;
        }
        return contentPart(item, contentIndex, ResponsesEvents.PART_REFUSAL, "refusal");
    }

    private JSONObject contentPart(JSONObject item, int contentIndex, String partType, String valueField)
            throws JSONException {
        JSONArray content = Jsons.optArray(item, "content");
        if (content == null) {
            content = new JSONArray();
            item.put("content", content);
        }
        while (content.length() <= contentIndex) {
            content.put(new JSONObject());
        }
        JSONObject part = content.optJSONObject(contentIndex);
        if (part == null || part.length() == 0) {
            part = new JSONObject();
            part.put("type", partType);
            part.put(valueField, "");
            content.put(contentIndex, part);
        }
        if (!part.has("type")) {
            part.put("type", partType);
        }
        return part;
    }

    private void putPart(int outputIndex, int contentIndex, JSONObject part) throws JSONException {
        JSONObject item = typedItem(outputIndex, ResponsesEvents.ITEM_MESSAGE);
        if (item == null) {
            return;
        }
        JSONArray content = Jsons.optArray(item, "content");
        if (content == null) {
            content = new JSONArray();
            item.put("content", content);
        }
        while (content.length() <= contentIndex) {
            content.put(new JSONObject());
        }
        content.put(contentIndex, part);
    }

    private void writeOutput() {
        try {
            JSONArray copy = new JSONArray();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item != null && item.length() > 0) {
                    copy.put(Jsons.copy(item));
                }
            }
            response.put("output", copy);
        } catch (JSONException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
