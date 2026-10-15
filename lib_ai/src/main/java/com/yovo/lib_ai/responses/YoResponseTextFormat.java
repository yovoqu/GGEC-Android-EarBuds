package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class YoResponseTextFormat {

    public enum Kind {
        TEXT,
        JSON_OBJECT,
        JSON_SCHEMA
    }

    private final Kind kind;
    @Nullable
    private final String name;
    @Nullable
    private final JSONObject schema;
    private final boolean strict;

    private YoResponseTextFormat(Kind kind, @Nullable String name, @Nullable JSONObject schema, boolean strict) {
        this.kind = kind;
        this.name = name;
        this.schema = schema;
        this.strict = strict;
    }

    @NonNull
    public static YoResponseTextFormat text() {
        return new YoResponseTextFormat(Kind.TEXT, null, null, false);
    }

    @NonNull
    public static YoResponseTextFormat jsonObject() {
        return new YoResponseTextFormat(Kind.JSON_OBJECT, null, null, false);
    }

    @NonNull
    public static YoResponseTextFormat jsonSchema(@NonNull String name, @NonNull JSONObject schema, boolean strict) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("schema name is required");
        }
        return new YoResponseTextFormat(Kind.JSON_SCHEMA, name, copy(schema), strict);
    }

    @NonNull
    public Kind kind() {
        return kind;
    }

    @Nullable
    public String name() {
        return name;
    }

    @Nullable
    public JSONObject schema() {
        return schema == null ? null : copy(schema);
    }

    public boolean strict() {
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
