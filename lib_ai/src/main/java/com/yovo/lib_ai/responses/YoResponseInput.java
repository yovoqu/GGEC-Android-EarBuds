package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class YoResponseInput {

    public enum Type {
        MESSAGE,
        FUNCTION_CALL,
        FUNCTION_CALL_OUTPUT,
        RAW
    }

    private final Type type;
    @Nullable
    private final String role;
    @Nullable
    private final String text;
    @Nullable
    private final String callId;
    @Nullable
    private final String name;
    @Nullable
    private final String arguments;
    @Nullable
    private final String output;
    @Nullable
    private final JSONObject raw;

    private YoResponseInput(
            Type type,
            @Nullable String role,
            @Nullable String text,
            @Nullable String callId,
            @Nullable String name,
            @Nullable String arguments,
            @Nullable String output,
            @Nullable JSONObject raw) {
        this.type = type;
        this.role = role;
        this.text = text;
        this.callId = callId;
        this.name = name;
        this.arguments = arguments;
        this.output = output;
        this.raw = raw;
    }

    @NonNull
    public static YoResponseInput userText(@NonNull String text) {
        return message("user", text);
    }

    @NonNull
    public static YoResponseInput message(@NonNull String role, @NonNull String text) {
        if (role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        return new YoResponseInput(Type.MESSAGE, role, text, null, null, null, null, null);
    }

    @NonNull
    public static YoResponseInput functionCall(
            @NonNull String callId,
            @NonNull String name,
            @NonNull String arguments) {
        if (callId.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("callId and name are required");
        }
        return new YoResponseInput(Type.FUNCTION_CALL, null, null, callId, name, arguments, null, null);
    }

    @NonNull
    public static YoResponseInput functionCallOutput(@NonNull String callId, @NonNull String output) {
        if (callId.isBlank()) {
            throw new IllegalArgumentException("callId is required");
        }
        return new YoResponseInput(Type.FUNCTION_CALL_OUTPUT, null, null, callId, null, null, output, null);
    }

    @NonNull
    public static YoResponseInput raw(@NonNull JSONObject raw) {
        try {
            return new YoResponseInput(Type.RAW, null, null, null, null, null, null, new JSONObject(raw.toString()));
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }

    @NonNull
    public Type type() {
        return type;
    }

    @Nullable
    public String role() {
        return role;
    }

    @Nullable
    public String text() {
        return text;
    }

    @Nullable
    public String callId() {
        return callId;
    }

    @Nullable
    public String name() {
        return name;
    }

    @Nullable
    public String arguments() {
        return arguments;
    }

    @Nullable
    public String output() {
        return output;
    }

    @Nullable
    public JSONObject raw() {
        if (raw == null) {
            return null;
        }
        try {
            return new JSONObject(raw.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
