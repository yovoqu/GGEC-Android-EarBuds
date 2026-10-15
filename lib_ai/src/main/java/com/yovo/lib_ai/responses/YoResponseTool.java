package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class YoResponseTool {

    private final String name;
    @Nullable
    private final String description;
    private final JSONObject parameters;
    @Nullable
    private final Boolean strict;

    private YoResponseTool(
            String name,
            @Nullable String description,
            JSONObject parameters,
            @Nullable Boolean strict) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.strict = strict;
    }

    @NonNull
    public static YoResponseTool function(
            @NonNull String name,
            @Nullable String description,
            @Nullable JSONObject parameters) {
        return function(name, description, parameters, null);
    }

    @NonNull
    public static YoResponseTool function(
            @NonNull String name,
            @Nullable String description,
            @Nullable JSONObject parameters,
            @Nullable Boolean strict) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("tool name is required");
        }
        JSONObject schema = parameters == null ? new JSONObject() : copy(parameters);
        return new YoResponseTool(name, description, schema, strict);
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
    public Boolean strict() {
        return strict;
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }
}
