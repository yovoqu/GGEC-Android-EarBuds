package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class YoResponseOutputItem {

    private final String type;
    @Nullable
    private final String id;
    @Nullable
    private final String role;
    @Nullable
    private final String status;
    @Nullable
    private final String text;
    @Nullable
    private final String name;
    @Nullable
    private final String callId;
    @Nullable
    private final String arguments;
    private final String rawJson;

    public YoResponseOutputItem(
            @NonNull String type,
            @Nullable String id,
            @Nullable String role,
            @Nullable String status,
            @Nullable String text,
            @Nullable String name,
            @Nullable String callId,
            @Nullable String arguments,
            @NonNull String rawJson) {
        this.type = type;
        this.id = id;
        this.role = role;
        this.status = status;
        this.text = text;
        this.name = name;
        this.callId = callId;
        this.arguments = arguments;
        this.rawJson = rawJson;
    }

    @NonNull
    public String type() {
        return type;
    }

    @Nullable
    public String id() {
        return id;
    }

    @Nullable
    public String role() {
        return role;
    }

    @Nullable
    public String status() {
        return status;
    }

    @Nullable
    public String text() {
        return text;
    }

    @Nullable
    public String name() {
        return name;
    }

    @Nullable
    public String callId() {
        return callId;
    }

    @Nullable
    public String arguments() {
        return arguments;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }
}
