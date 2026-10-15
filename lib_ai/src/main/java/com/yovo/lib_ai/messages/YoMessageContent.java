package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class YoMessageContent {

    public enum Type {
        TEXT,
        TOOL_USE,
        TOOL_RESULT,
        THINKING,
        RAW
    }

    private final Type type;
    @Nullable
    private final String text;
    @Nullable
    private final String id;
    @Nullable
    private final String name;
    @Nullable
    private final JSONObject input;
    @Nullable
    private final String signature;
    @Nullable
    private final JSONObject raw;

    private YoMessageContent(
            Type type,
            @Nullable String text,
            @Nullable String id,
            @Nullable String name,
            @Nullable JSONObject input,
            @Nullable String signature,
            @Nullable JSONObject raw) {
        this.type = type;
        this.text = text;
        this.id = id;
        this.name = name;
        this.input = input;
        this.signature = signature;
        this.raw = raw;
    }

    @NonNull
    public static YoMessageContent text(@NonNull String text) {
        return new YoMessageContent(Type.TEXT, text, null, null, null, null, null);
    }

    @NonNull
    public static YoMessageContent toolUse(@NonNull String id, @NonNull String name, @NonNull JSONObject input) {
        if (id.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("tool use id and name are required");
        }
        return new YoMessageContent(Type.TOOL_USE, null, id, name, copy(input), null, null);
    }

    @NonNull
    public static YoMessageContent toolResult(@NonNull String toolUseId, @NonNull String content) {
        if (toolUseId.isBlank()) {
            throw new IllegalArgumentException("toolUseId is required");
        }
        return new YoMessageContent(Type.TOOL_RESULT, content, toolUseId, null, null, null, null);
    }

    @NonNull
    public static YoMessageContent thinking(@NonNull String thinking) {
        return thinking(thinking, null);
    }

    @NonNull
    public static YoMessageContent thinking(@NonNull String thinking, @Nullable String signature) {
        return new YoMessageContent(Type.THINKING, thinking, null, null, null, signature, null);
    }

    @NonNull
    public static YoMessageContent raw(@NonNull JSONObject raw) {
        return new YoMessageContent(Type.RAW, null, null, null, null, null, copy(raw));
    }

    @NonNull
    public Type type() {
        return type;
    }

    @Nullable
    public String text() {
        return text;
    }

    @Nullable
    public String id() {
        return id;
    }

    @Nullable
    public String name() {
        return name;
    }

    @Nullable
    public String signature() {
        return signature;
    }

    @Nullable
    public JSONObject input() {
        return input == null ? null : copy(input);
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
