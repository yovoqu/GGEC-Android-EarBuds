package com.yovo.lib_ai.internal.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class Jsons {

    private Jsons() {
    }

    @NonNull
    public static JSONObject parseObject(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw new YoAiProtocolException("Response body is empty", raw);
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Response body is not a JSON object", raw, exception);
        }
    }

    @NonNull
    public static JSONObject copy(@Nullable JSONObject json) {
        if (json == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }

    public static void put(@NonNull JSONObject json, @NonNull String key, @Nullable Object value)
            throws JSONException {
        if (value != null) {
            json.put(key, value);
        }
    }

    @Nullable
    public static String optString(@Nullable JSONObject json, @NonNull String key) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        return String.valueOf(value);
    }

    @Nullable
    public static JSONObject optObject(@Nullable JSONObject json, @NonNull String key) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.optJSONObject(key);
    }

    @Nullable
    public static JSONArray optArray(@Nullable JSONObject json, @NonNull String key) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.optJSONArray(key);
    }

    @NonNull
    public static String truncate(@Nullable String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars);
    }
}
