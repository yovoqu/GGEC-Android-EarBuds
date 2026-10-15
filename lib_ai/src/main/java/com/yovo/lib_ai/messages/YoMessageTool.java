package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class YoMessageTool {

    private final String name;
    @Nullable
    private final String description;
    private final JSONObject inputSchema;

    private YoMessageTool(String name, @Nullable String description, JSONObject inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    @NonNull
    public static YoMessageTool function(
            @NonNull String name,
            @Nullable String description,
            @Nullable JSONObject inputSchema) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("tool name is required");
        }
        JSONObject schema = inputSchema == null ? new JSONObject() : copy(inputSchema);
        return new YoMessageTool(name, description, schema);
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
    public JSONObject inputSchema() {
        return copy(inputSchema);
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }
}
