package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class YoMessageTool {

    private final String name;
    @Nullable
    private final String description;
    private final JSONObject inputSchema;
    @Nullable
    private final Boolean strict;
    @Nullable
    private final JSONArray inputExamples;
    @Nullable
    private final Boolean deferLoading;
    @Nullable
    private final JSONObject cacheControl;

    private YoMessageTool(
            String name,
            @Nullable String description,
            JSONObject inputSchema,
            @Nullable Boolean strict,
            @Nullable JSONArray inputExamples,
            @Nullable Boolean deferLoading,
            @Nullable JSONObject cacheControl) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.strict = strict;
        this.inputExamples = inputExamples;
        this.deferLoading = deferLoading;
        this.cacheControl = cacheControl;
    }

    @NonNull
    public static YoMessageTool function(
            @NonNull String name,
            @Nullable String description,
            @Nullable JSONObject inputSchema) {
        return function(name, description, inputSchema, null, null, null);
    }

    @NonNull
    public static YoMessageTool function(
            @NonNull String name,
            @Nullable String description,
            @Nullable JSONObject inputSchema,
            @Nullable Boolean strict,
            @Nullable JSONArray inputExamples,
            @Nullable Boolean deferLoading) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("tool name is required");
        }
        JSONObject schema = inputSchema == null ? new JSONObject() : copy(inputSchema);
        return new YoMessageTool(
                name,
                description,
                schema,
                strict,
                inputExamples == null ? null : copy(inputExamples),
                deferLoading,
                null);
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

    @Nullable
    public Boolean strict() {
        return strict;
    }

    @Nullable
    public JSONArray inputExamples() {
        return inputExamples == null ? null : copy(inputExamples);
    }

    @Nullable
    public Boolean deferLoading() {
        return deferLoading;
    }

    @Nullable
    public JSONObject cacheControl() {
        return cacheControl == null ? null : copy(cacheControl);
    }

    /**
     * 返回带块级 prompt-cache 标记的工具副本。
     */
    @NonNull
    public YoMessageTool withCache(@Nullable JSONObject cacheControl) {
        return new YoMessageTool(
                name,
                description,
                copy(inputSchema),
                strict,
                inputExamples == null ? null : copy(inputExamples),
                deferLoading,
                cacheControl == null ? null : copy(cacheControl));
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }

    private static JSONArray copy(JSONArray array) {
        try {
            return new JSONArray(array.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON array", exception);
        }
    }
}
