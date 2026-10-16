package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class YoResponseInput {

    public enum Type {
        MESSAGE,
        FUNCTION_CALL,
        FUNCTION_CALL_OUTPUT
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
    private final List<YoResponseContentPart> parts;

    private YoResponseInput(
            Type type,
            @Nullable String role,
            @Nullable String text,
            @Nullable String callId,
            @Nullable String name,
            @Nullable String arguments,
            @Nullable String output,
            @NonNull List<YoResponseContentPart> parts) {
        this.type = type;
        this.role = role;
        this.text = text;
        this.callId = callId;
        this.name = name;
        this.arguments = arguments;
        this.output = output;
        this.parts = parts;
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
        return new YoResponseInput(Type.MESSAGE, role, text, null, null, null, null, List.of());
    }

    @NonNull
    public static YoResponseInput message(@NonNull String role, @NonNull YoResponseContentPart... parts) {
        if (role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        if (parts.length == 0) {
            throw new IllegalArgumentException("content parts are required");
        }
        return new YoResponseInput(Type.MESSAGE, role, null, null, null, null, null, List.of(parts));
    }

    @NonNull
    public static YoResponseInput functionCall(
            @NonNull String callId,
            @NonNull String name,
            @NonNull String arguments) {
        if (callId.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("callId and name are required");
        }
        return new YoResponseInput(Type.FUNCTION_CALL, null, null, callId, name, arguments, null, List.of());
    }

    @NonNull
    public static YoResponseInput functionCallOutput(@NonNull String callId, @NonNull String output) {
        if (callId.isBlank()) {
            throw new IllegalArgumentException("callId is required");
        }
        return new YoResponseInput(Type.FUNCTION_CALL_OUTPUT, null, null, callId, null, null, output, List.of());
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

    @NonNull
    public List<YoResponseContentPart> parts() {
        return parts;
    }
}
