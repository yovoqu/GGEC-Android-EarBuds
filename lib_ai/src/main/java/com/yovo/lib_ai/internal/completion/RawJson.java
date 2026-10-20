package com.yovo.lib_ai.internal.completion;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 适配器从各协议事件的原始 JSON 里取增量类型和思考内容。解析失败一律当作"没有"。
 */
final class RawJson {

    private RawJson() {
    }

    @Nullable
    static JSONObject parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException ignored) {
            return null;
        }
    }

    @Nullable
    static JSONObject firstChoice(@Nullable JSONObject json, String key) {
        JSONArray choices = json == null ? null : json.optJSONArray("choices");
        JSONObject choice = choices == null ? null : choices.optJSONObject(0);
        return choice == null ? null : choice.optJSONObject(key);
    }

    @Nullable
    static String string(@Nullable JSONObject json, String... keys) {
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            if (json.has(key) && !json.isNull(key) && json.opt(key) instanceof String text && !text.isEmpty()) {
                return text;
            }
        }
        return null;
    }
}
