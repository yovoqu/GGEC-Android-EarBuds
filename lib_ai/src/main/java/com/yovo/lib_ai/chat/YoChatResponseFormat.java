package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class YoChatResponseFormat {

    public enum Kind {
        TEXT,
        JSON_OBJECT,
        JSON_SCHEMA
    }

    private final Kind kind;
    @Nullable
    private final String schemaName;
    @Nullable
    private final JSONObject schema;
    private final boolean strict;

    private YoChatResponseFormat(
            Kind kind,
            @Nullable String schemaName,
            @Nullable JSONObject schema,
            boolean strict) {
        this.kind = kind;
        this.schemaName = schemaName;
        this.schema = schema;
        this.strict = strict;
    }

    @NonNull
    public static YoChatResponseFormat text() {
        return new YoChatResponseFormat(Kind.TEXT, null, null, false);
    }

    @NonNull
    public static YoChatResponseFormat jsonObject() {
        return new YoChatResponseFormat(Kind.JSON_OBJECT, null, null, false);
    }

    @NonNull
    public static YoChatResponseFormat jsonSchema(
            @NonNull String name,
            @NonNull JSONObject schema,
            boolean strict) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("schema name is required");
        }
        return new YoChatResponseFormat(Kind.JSON_SCHEMA, name, copy(schema), strict);
    }

    @NonNull
    public Kind kind() {
        return kind;
    }

    @Nullable
    public String schemaName() {
        return schemaName;
    }

    @Nullable
    public JSONObject schema() {
        return schema == null ? null : copy(schema);
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }

    public boolean strict() {
        return strict;
    }
}
