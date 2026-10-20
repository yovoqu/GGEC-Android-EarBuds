package com.ggec.earbuds.data.ai.json;

import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiMessageFailure;
import com.ggec.earbuds.domain.ai.model.AiMessageStatus;
import com.ggec.earbuds.domain.ai.model.AiRole;
import com.ggec.earbuds.domain.ai.model.AiUsage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话文档与历史索引的编解码。同样宽容：坏掉的单条消息跳过，不让整段对话打不开。
 */
public final class AiConversationJson {

    static final int VERSION = 1;

    public String encode(AiConversation conversation) {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "v", VERSION);
        JsonValues.put(root, "id", conversation.id());
        JsonValues.put(root, "title", conversation.title());
        JsonValues.put(root, "createdAt", conversation.createdAtMillis());
        JsonValues.put(root, "updatedAt", conversation.updatedAtMillis());
        JSONArray messages = new JSONArray();
        for (AiMessage message : conversation.messages()) {
            messages.put(encodeMessage(message));
        }
        JsonValues.put(root, "messages", messages);
        return root.toString();
    }

    /**
     * @throws JSONException 文档不是合法的 JSON 对象，或缺少对话 id
     */
    public AiConversation decode(String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        String id = JsonValues.string(root, "id");
        if (id == null) {
            throw new JSONException("conversation id is missing");
        }
        List<AiMessage> messages = new ArrayList<>();
        JSONArray array = root.optJSONArray("messages");
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            AiMessage message = entry == null ? null : decodeMessage(entry);
            if (message != null) {
                messages.add(message);
            }
        }
        long createdAt = JsonValues.number(root, "createdAt", 0L);
        return new AiConversation(id, JsonValues.string(root, "title", ""), createdAt,
                JsonValues.number(root, "updatedAt", createdAt), messages);
    }

    public String encodeIndex(List<AiConversationSummary> summaries) {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "v", VERSION);
        JSONArray items = new JSONArray();
        for (AiConversationSummary summary : summaries) {
            JSONObject item = new JSONObject();
            JsonValues.put(item, "id", summary.id());
            JsonValues.put(item, "title", summary.title());
            JsonValues.put(item, "updatedAt", summary.updatedAtMillis());
            JsonValues.put(item, "count", summary.messageCount());
            JsonValues.put(item, "preview", summary.preview());
            items.put(item);
        }
        JsonValues.put(root, "items", items);
        return root.toString();
    }

    /**
     * @throws JSONException 索引不是合法的 JSON 对象
     */
    public List<AiConversationSummary> decodeIndex(String text) throws JSONException {
        JSONArray items = new JSONObject(text).optJSONArray("items");
        List<AiConversationSummary> summaries = new ArrayList<>();
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            String id = item == null ? null : JsonValues.string(item, "id");
            if (id == null) {
                continue;
            }
            Integer count = JsonValues.integer(item, "count");
            summaries.add(new AiConversationSummary(id, JsonValues.string(item, "title", ""),
                    JsonValues.number(item, "updatedAt", 0L), count == null ? 0 : count,
                    JsonValues.string(item, "preview", "")));
        }
        return summaries;
    }

    private static JSONObject encodeMessage(AiMessage message) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "id", message.id());
        JsonValues.put(json, "role", message.role().name());
        JsonValues.put(json, "text", message.text());
        JsonValues.put(json, "reasoning", message.reasoning());
        JsonValues.put(json, "status", message.status().name());
        if (message.failure() != null) {
            JsonValues.put(json, "failureKind", message.failure().kind().name());
            JsonValues.put(json, "failureDetail", message.failure().detail());
        }
        JsonValues.put(json, "finish", message.finishReason().name());
        JsonValues.put(json, "model", message.modelLabel());
        JsonValues.put(json, "createdAt", message.createdAtMillis());
        JsonValues.put(json, "elapsed", message.elapsedMillis());
        if (message.usage() != null) {
            JSONObject usage = new JSONObject();
            JsonValues.put(usage, "in", message.usage().inputTokens());
            JsonValues.put(usage, "out", message.usage().outputTokens());
            JsonValues.put(json, "usage", usage);
        }
        return json;
    }

    private static AiMessage decodeMessage(JSONObject json) {
        String id = JsonValues.string(json, "id");
        String text = JsonValues.string(json, "text");
        if (id == null || text == null) {
            return null;
        }
        AiMessageFailure failure = null;
        String failureKind = JsonValues.string(json, "failureKind");
        if (failureKind != null) {
            failure = new AiMessageFailure(AiFailureKind.fromName(failureKind),
                    JsonValues.string(json, "failureDetail", ""));
        }
        AiUsage usage = null;
        JSONObject usageJson = json.optJSONObject("usage");
        if (usageJson != null) {
            Integer input = JsonValues.integer(usageJson, "in");
            Integer output = JsonValues.integer(usageJson, "out");
            usage = new AiUsage(input == null ? 0 : input, output == null ? 0 : output);
        }
        return new AiMessage(
                id,
                AiRole.fromName(JsonValues.string(json, "role", "USER")),
                text,
                JsonValues.string(json, "reasoning"),
                AiMessageStatus.fromName(JsonValues.string(json, "status", "COMPLETE")),
                failure,
                AiFinishReason.fromName(JsonValues.string(json, "finish", "STOP")),
                JsonValues.string(json, "model"),
                JsonValues.number(json, "createdAt", 0L),
                JsonValues.number(json, "elapsed", 0L),
                usage);
    }
}
