package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.retrieval.YoRerankItem;
import com.yovo.lib_ai.retrieval.YoRerankRequest;
import com.yovo.lib_ai.retrieval.YoRerankResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** DashScope {@code text-rerank} 报文的编码与解码。 */
public final class RerankCodec {

    private RerankCodec() {
    }

    @NonNull
    public static JSONObject encode(@NonNull YoRerankRequest request) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", request.model());
        JSONObject input = new JSONObject();
        input.put("query", request.query());
        JSONArray documents = new JSONArray();
        for (Object document : request.documents()) {
            documents.put(document);
        }
        input.put("documents", documents);
        json.put("input", input);
        JSONObject parameters = new JSONObject();
        if (request.topN() != null) {
            parameters.put("top_n", request.topN());
        }
        if (request.returnDocuments() != null) {
            parameters.put("return_documents", request.returnDocuments());
        }
        if (request.instruct() != null) {
            parameters.put("instruct", request.instruct());
        }
        if (request.fps() != null) {
            parameters.put("fps", request.fps());
        }
        if (parameters.length() > 0) {
            json.put("parameters", parameters);
        }
        return json;
    }

    @NonNull
    public static YoRerankResult decode(@NonNull String body) {
        JSONObject root = RetrievalJson.parseObject(body, "Rerank response");
        JSONObject output = root.optJSONObject("output");
        JSONArray results = output == null ? null : output.optJSONArray("results");
        if (results == null) {
            throw new YoAiProtocolException("Rerank response has no output.results array", body);
        }
        List<YoRerankItem> items = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject entry = results.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            Integer index = RetrievalJson.integer(entry, "index");
            items.add(new YoRerankItem(
                    index == null ? i : index,
                    RetrievalJson.doubleValue(entry, "relevance_score"),
                    document(entry.opt("document"))));
        }
        // 服务端通常已按分数降序；这里再排一次，保证任何厂商都满足"降序"的验收口径。
        items.sort(Comparator.comparingDouble(YoRerankItem::score).reversed());
        JSONObject usage = root.optJSONObject("usage");
        return new YoRerankResult(
                items,
                RetrievalJson.integer(usage, "prompt_tokens"),
                RetrievalJson.integer(usage, "total_tokens"),
                body);
    }

    /** 结果被 top_n 截断到请求的上限。 */
    @NonNull
    public static YoRerankResult truncate(@NonNull YoRerankResult result, @Nullable Integer topN) {
        if (topN == null || result.results().size() <= topN) {
            return result;
        }
        List<YoRerankItem> kept = new ArrayList<>(result.results().subList(0, topN));
        return new YoRerankResult(kept, result.promptTokens(), result.totalTokens(), result.rawJson());
    }

    @Nullable
    private static String document(@Nullable Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof String text) {
            return text;
        }
        if (raw instanceof JSONObject object) {
            String text = RetrievalJson.string(object, "text");
            return text != null ? text : object.toString();
        }
        return String.valueOf(raw);
    }
}
