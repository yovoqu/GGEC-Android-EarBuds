package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 实时会话里的一个函数工具。
 *
 * <p>形状和聊天补全不同：Realtime 把 {@code name} 放在工具对象顶层，
 * 而不是再套一层 {@code function}。编解码时如果套成聊天那种形状，服务端会直接拒会话。</p>
 */
public final class YoRealtimeTool {

    @NonNull
    private final String name;
    @Nullable
    private final String description;
    @NonNull
    private final JSONObject parameters;

    private YoRealtimeTool(
            @NonNull String name,
            @Nullable String description,
            @NonNull JSONObject parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    @NonNull
    public static YoRealtimeTool function(
            @NonNull String name,
            @Nullable String description,
            @Nullable JSONObject parameters) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("tool name is required");
        }
        return new YoRealtimeTool(name.trim(), blankToNull(description), copy(parameters));
    }

    @NonNull
    public String name() {
        return name;
    }

    @Nullable
    public String description() {
        return description;
    }

    @NonNull
    public JSONObject parameters() {
        return copy(parameters);
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @NonNull
    private static JSONObject copy(@Nullable JSONObject json) {
        if (json == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid tool parameters", exception);
        }
    }
}
