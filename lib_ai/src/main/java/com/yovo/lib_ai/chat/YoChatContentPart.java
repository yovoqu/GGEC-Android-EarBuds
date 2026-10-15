package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 消息内容片段。未知结构用 {@link #raw(JSONObject)} 原样发送。
 */
public final class YoChatContentPart {

    @Nullable
    private final String text;
    @Nullable
    private final JSONObject raw;

    private YoChatContentPart(@Nullable String text, @Nullable JSONObject raw) {
        this.text = text;
        this.raw = raw;
    }

    @NonNull
    public static YoChatContentPart text(@NonNull String text) {
        return new YoChatContentPart(text, null);
    }

    @NonNull
    public static YoChatContentPart raw(@NonNull JSONObject raw) {
        return new YoChatContentPart(null, copy(raw));
    }

    @Nullable
    public String text() {
        return text;
    }

    @Nullable
    public JSONObject raw() {
        return raw == null ? null : copy(raw);
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }
}
