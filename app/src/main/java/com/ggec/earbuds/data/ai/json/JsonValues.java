package com.ggec.earbuds.data.ai.json;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 编解码共用的取值工具。org.json 在 JSON null 和缺失键上的行为不一致（会读出字符串 "null"），
 * 这里统一成"缺失或 null 都是没有"。
 */
final class JsonValues {

    private JsonValues() {
    }

    @Nullable
    static String string(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        return value instanceof String text ? text : null;
    }

    static String string(JSONObject json, String key, String fallback) {
        String value = string(json, key);
        return value == null ? fallback : value;
    }

    @Nullable
    static Integer integer(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.opt(key) instanceof Number number ? number.intValue() : null;
    }

    static long number(JSONObject json, String key, long fallback) {
        if (!json.has(key) || json.isNull(key)) {
            return fallback;
        }
        return json.opt(key) instanceof Number number ? number.longValue() : fallback;
    }

    static boolean flag(JSONObject json, String key, boolean fallback) {
        if (!json.has(key) || json.isNull(key)) {
            return fallback;
        }
        return json.opt(key) instanceof Boolean value ? value : fallback;
    }

    static void put(JSONObject json, String key, @Nullable Object value) {
        if (value == null) {
            return;
        }
        try {
            json.put(key, value);
        } catch (JSONException impossible) {
            throw new IllegalStateException("Cannot encode " + key, impossible);
        }
    }
}
