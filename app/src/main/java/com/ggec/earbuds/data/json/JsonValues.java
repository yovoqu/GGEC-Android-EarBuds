package com.ggec.earbuds.data.json;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 数据层共用的 JSON 取值工具。
 *
 * <p>org.json 在 JSON null 和缺失键上的行为不一致（会读出字符串 "null"），
 * 这里统一成"缺失或 null 都是没有"。单源：任何 feature 的编解码都用这一份，
 * 不再各抄一套。</p>
 */
public final class JsonValues {

    private JsonValues() {
    }

    @Nullable
    public static String string(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        return value instanceof String text ? text : null;
    }

    public static String string(JSONObject json, String key, String fallback) {
        String value = string(json, key);
        return value == null ? fallback : value;
    }

    @Nullable
    public static Integer integer(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.opt(key) instanceof Number number ? number.intValue() : null;
    }

    public static long number(JSONObject json, String key, long fallback) {
        if (!json.has(key) || json.isNull(key)) {
            return fallback;
        }
        return json.opt(key) instanceof Number number ? number.longValue() : fallback;
    }

    public static boolean flag(JSONObject json, String key, boolean fallback) {
        if (!json.has(key) || json.isNull(key)) {
            return fallback;
        }
        return json.opt(key) instanceof Boolean value ? value : fallback;
    }

    public static void put(JSONObject json, String key, @Nullable Object value) {
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
