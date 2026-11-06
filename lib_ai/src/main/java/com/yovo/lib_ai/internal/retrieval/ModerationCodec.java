package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.retrieval.YoModerationCategory;
import com.yovo.lib_ai.retrieval.YoModerationRequest;
import com.yovo.lib_ai.retrieval.YoModerationResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAI {@code /v1/moderations} 报文的编码与解码。 */
public final class ModerationCodec {

    private ModerationCodec() {
    }

    @NonNull
    public static JSONObject encode(@NonNull YoModerationRequest request) throws JSONException {
        JSONObject json = new JSONObject();
        if (request.model() != null) {
            json.put("model", request.model());
        }
        List<Object> input = request.input();
        if (input.size() == 1 && input.get(0) instanceof String text) {
            json.put("input", text);
        } else {
            JSONArray array = new JSONArray();
            for (Object item : input) {
                array.put(item);
            }
            json.put("input", array);
        }
        return json;
    }

    @NonNull
    public static YoModerationResult decode(@NonNull String body) {
        JSONObject root = RetrievalJson.parseObject(body, "Moderation response");
        JSONArray results = root.optJSONArray("results");
        if (results == null) {
            throw new YoAiProtocolException("Moderation response has no results array", body);
        }
        boolean flagged = false;
        Map<String, CategoryScore> merged = new LinkedHashMap<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.optJSONObject(i);
            if (result == null) {
                continue;
            }
            flagged |= result.optBoolean("flagged", false);
            merge(result.optJSONObject("categories"), result.optJSONObject("category_scores"),
                    result.optJSONObject("category_applied_input_types"), merged);
        }
        List<YoModerationCategory> categories = new ArrayList<>();
        for (Map.Entry<String, CategoryScore> entry : merged.entrySet()) {
            CategoryScore score = entry.getValue();
            categories.add(new YoModerationCategory(
                    entry.getKey(), score.flagged, score.score, score.appliedInputTypes));
        }
        return new YoModerationResult(flagged, categories, RetrievalJson.string(root, "model"), body);
    }

    private static void merge(
            @Nullable JSONObject categories,
            @Nullable JSONObject scores,
            @Nullable JSONObject applied,
            @NonNull Map<String, CategoryScore> merged) {
        if (categories == null && scores == null) {
            return;
        }
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (categories != null) {
            for (Iterator<String> keys = categories.keys(); keys.hasNext(); ) {
                names.add(keys.next());
            }
        }
        if (scores != null) {
            for (Iterator<String> keys = scores.keys(); keys.hasNext(); ) {
                names.add(keys.next());
            }
        }
        for (String name : names) {
            boolean isFlagged = categories != null && categories.optBoolean(name, false);
            double score = scores == null ? 0d : scores.optDouble(name, 0d);
            List<String> appliedTypes = new ArrayList<>();
            Object appliedValue = applied == null ? null : applied.opt(name);
            if (appliedValue instanceof JSONArray array) {
                // OpenAI 实际形状：category_applied_input_types.violence = ["text","image"]。
                for (int i = 0; i < array.length(); i++) {
                    if (array.opt(i) instanceof String type && !type.isBlank()) {
                        appliedTypes.add(type);
                    }
                }
            } else if (appliedValue instanceof JSONObject object) {
                for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    if (object.optBoolean(key, false)) {
                        appliedTypes.add(key);
                    }
                }
            }
            CategoryScore existing = merged.get(name);
            if (existing == null) {
                merged.put(name, new CategoryScore(isFlagged, score, appliedTypes));
            } else {
                existing.flagged |= isFlagged;
                existing.score = Math.max(existing.score, score);
                for (String type : appliedTypes) {
                    if (!existing.appliedInputTypes.contains(type)) {
                        existing.appliedInputTypes.add(type);
                    }
                }
            }
        }
    }

    private static final class CategoryScore {
        boolean flagged;
        double score;
        final List<String> appliedInputTypes;

        private CategoryScore(boolean flagged, double score, List<String> appliedInputTypes) {
            this.flagged = flagged;
            this.score = score;
            this.appliedInputTypes = appliedInputTypes;
        }
    }
}
