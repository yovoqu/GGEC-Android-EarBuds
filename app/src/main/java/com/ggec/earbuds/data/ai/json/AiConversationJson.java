package com.ggec.earbuds.data.ai.json;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.json.JsonValues;
import com.ggec.earbuds.data.store.DocumentFormatException;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 对话文档与历史索引的编解码，严格版本化。
 *
 * <p>解码第一步读 {@code v}，不等于当前版本直接抛 {@link DocumentFormatException}；
 * 没有旧键名迁移，也没有"坏掉的单条消息跳过"——一条消息读不出来，这段对话就是损坏的，
 * 静默丢掉半段历史等于伪造上下文。费用写成字符串，避免 JSON 数字经 double 往返丢精度。</p>
 */
public final class AiConversationJson {

    static final int VERSION = 1;

    /** 列表摘要的格式版本。摘要内容变了就 +1，旧索引会被当成损坏并按会话文件重建。 */
    static final int INDEX_VERSION = 2;

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
     * @throws DocumentFormatException 文档不是合法 JSON 对象、版本不符、必填字段缺失、
     *                                类型不符或枚举值未知
     */
    public AiConversation decode(String text) {
        JSONObject root = root(text);
        requireVersion(root, VERSION, "conversation");
        List<AiMessage> messages = new ArrayList<>();
        JSONArray array = requiredArray(root, "messages");
        for (int i = 0; i < array.length(); i++) {
            messages.add(decodeMessage(requiredEntry(array, i, "messages")));
        }
        return new AiConversation(
                JsonValues.requiredString(root, "id"),
                JsonValues.requiredString(root, "title"),
                JsonValues.requiredLong(root, "createdAt"),
                JsonValues.requiredLong(root, "updatedAt"),
                messages);
    }

    public String encodeIndex(List<AiConversationSummary> summaries) {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "v", INDEX_VERSION);
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
     * @throws DocumentFormatException 索引不是合法 JSON 对象、版本不符或条目损坏。
     *                                 索引是派生数据，由仓库据此按会话文件重建。
     */
    public List<AiConversationSummary> decodeIndex(String text) {
        JSONObject root = root(text);
        requireVersion(root, INDEX_VERSION, "conversation index");
        JSONArray items = requiredArray(root, "items");
        List<AiConversationSummary> summaries = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = requiredEntry(items, i, "items");
            summaries.add(new AiConversationSummary(
                    JsonValues.requiredString(item, "id"),
                    JsonValues.requiredString(item, "title"),
                    JsonValues.requiredLong(item, "updatedAt"),
                    JsonValues.requiredInt(item, "count"),
                    JsonValues.requiredString(item, "preview")));
        }
        return summaries;
    }

    private static JSONObject root(String text) {
        try {
            return new JSONObject(text);
        } catch (JSONException malformed) {
            throw new DocumentFormatException("document",
                    "conversation document is not a JSON object", malformed);
        }
    }

    private static void requireVersion(JSONObject root, int current, String what) {
        int version = JsonValues.requiredInt(root, "v");
        if (version != current) {
            throw new DocumentFormatException("v", what + " has unsupported version " + version);
        }
    }

    private static JSONArray requiredArray(JSONObject json, String key) {
        JSONArray value = json.optJSONArray(key);
        if (value == null) {
            throw new DocumentFormatException(key, "missing required array " + key);
        }
        return value;
    }

    private static JSONObject requiredEntry(JSONArray array, int index, String path) {
        JSONObject entry = array.optJSONObject(index);
        if (entry == null) {
            throw new DocumentFormatException(path + "[" + index + "]",
                    path + "[" + index + "] is not an object");
        }
        return entry;
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
        }
        JsonValues.put(json, "finish", message.finishReason().name());
        JsonValues.put(json, "model", message.modelLabel());
        JsonValues.put(json, "createdAt", message.createdAtMillis());
        JsonValues.put(json, "elapsed", message.elapsedMillis());
        if (message.usage() != null) {
            JSONObject usage = new JSONObject();
            JsonValues.put(usage, "in", message.usage().inputTokens());
            JsonValues.put(usage, "out", message.usage().outputTokens());
            JsonValues.put(usage, "total", message.usage().totalTokens());
            if (message.usage().cost() != null) {
                // 写成字符串：JSON 数字经 double 往返会掉分，钱不能这么丢。
                JsonValues.put(usage, "cost", message.usage().cost().toPlainString());
            }
            JsonValues.put(usage, "cached", message.usage().cachedTokens());
            JsonValues.put(usage, "cacheWrite", message.usage().cacheCreationTokens());
            JsonValues.put(json, "usage", usage);
        }
        return json;
    }

    private static AiMessage decodeMessage(JSONObject json) {
        String id = JsonValues.requiredString(json, "id");
        AiRole role = AiRole.fromName(JsonValues.requiredString(json, "role"))
                .orElseThrow(() -> new DocumentFormatException("role",
                        "unknown message role in the conversation document"));
        AiMessageStatus status = AiMessageStatus.fromName(JsonValues.requiredString(json, "status"))
                .orElseThrow(() -> new DocumentFormatException("status",
                        "unknown message status in the conversation document"));
        AiFinishReason finish = AiFinishReason.fromName(JsonValues.requiredString(json, "finish"))
                .orElseThrow(() -> new DocumentFormatException("finish",
                        "unknown finish reason in the conversation document"));
        return new AiMessage(
                id,
                role,
                JsonValues.requiredString(json, "text"),
                JsonValues.optionalString(json, "reasoning").orElse(null),
                status,
                decodeFailure(json),
                finish,
                JsonValues.optionalString(json, "model").orElse(null),
                JsonValues.requiredLong(json, "createdAt"),
                JsonValues.requiredLong(json, "elapsed"),
                decodeUsage(json.optJSONObject("usage")));
    }

    @Nullable
    private static AiMessageFailure decodeFailure(JSONObject json) {
        Optional<String> kind = JsonValues.optionalString(json, "failureKind");
        if (kind.isEmpty()) {
            return null;
        }
        AiFailureKind parsed = AiFailureKind.fromName(kind.get())
                .orElseThrow(() -> new DocumentFormatException("failureKind",
                        "unknown failure kind " + kind.get() + " in the conversation document"));
        return new AiMessageFailure(parsed);
    }

    @Nullable
    private static AiUsage decodeUsage(@Nullable JSONObject json) {
        if (json == null) {
            return null;
        }
        return new AiUsage(
                JsonValues.requiredInt(json, "in"),
                JsonValues.requiredInt(json, "out"),
                JsonValues.requiredInt(json, "total"),
                decodeCost(json),
                JsonValues.optionalInt(json, "cached").orElse(null),
                JsonValues.optionalInt(json, "cacheWrite").orElse(null));
    }

    @Nullable
    private static BigDecimal decodeCost(JSONObject usage) {
        Optional<String> text = JsonValues.optionalString(usage, "cost");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.get().strip());
        } catch (NumberFormatException malformed) {
            throw new DocumentFormatException("cost",
                    "usage cost is not a decimal number in the conversation document", malformed);
        }
    }
}
