package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Responses 的一个工具声明：要么是客户端 function，要么是服务端内置工具。
 * 服务端工具只带官方 wire type 和可选 options，不再伪装成 function。
 */
public final class YoResponseTool {

    @Nullable
    private final String name;
    @Nullable
    private final String description;
    private final JSONObject parameters;
    @Nullable
    private final Boolean strict;
    @Nullable
    private final ServerTool serverTool;

    private YoResponseTool(
            @Nullable String name,
            @Nullable String description,
            JSONObject parameters,
            @Nullable Boolean strict,
            @Nullable ServerTool serverTool) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.strict = strict;
        this.serverTool = serverTool;
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
        return new YoResponseTool(name, description, schema, strict, null);
    }

    @NonNull
    public static YoResponseTool server(@NonNull ServerTool serverTool) {
        return new YoResponseTool(null, null, new JSONObject(), null, serverTool);
    }

    @NonNull
    public static YoResponseTool server(@NonNull ServerTool.Kind kind) {
        return server(ServerTool.of(kind));
    }

    @NonNull
    public static YoResponseTool server(@NonNull ServerTool.Kind kind, @Nullable JSONObject options) {
        return server(ServerTool.of(kind, options));
    }

    @Nullable
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

    @Nullable
    public ServerTool serverTool() {
        return serverTool;
    }

    /**
     * Responses 官方服务端内置工具。options 原样并入 wire JSON（type 由 kind 决定）。
     */
    public static final class ServerTool {

        public enum Kind {
            WEB_SEARCH("web_search"),
            FILE_SEARCH("file_search"),
            CODE_INTERPRETER("code_interpreter"),
            COMPUTER("computer"),
            IMAGE_GENERATION("image_generation"),
            MCP("mcp"),
            TOOL_SEARCH("tool_search");

            private final String wireType;

            Kind(String wireType) {
                this.wireType = wireType;
            }

            @NonNull
            public String wireType() {
                return wireType;
            }
        }

        private final Kind kind;
        private final JSONObject options;

        private ServerTool(Kind kind, JSONObject options) {
            this.kind = kind;
            this.options = options;
        }

        @NonNull
        public static ServerTool of(@NonNull Kind kind) {
            return of(kind, null);
        }

        @NonNull
        public static ServerTool of(@NonNull Kind kind, @Nullable JSONObject options) {
            if (kind == null) {
                throw new IllegalArgumentException("server tool kind is required");
            }
            JSONObject copy = options == null ? new JSONObject() : copy(options);
            return new ServerTool(kind, copy);
        }

        @NonNull
        public Kind kind() {
            return kind;
        }

        @NonNull
        public String wireType() {
            return kind.wireType();
        }

        @NonNull
        public JSONObject options() {
            return copy(options);
        }
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }
}
