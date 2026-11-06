package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Anthropic 服务端工具声明。wire 上只发 {@code type}/{@code name} 和可选 options。
 */
public final class YoMessageServerTool {

    public enum Kind {
        WEB_SEARCH("web_search_20250305", "web_search"),
        WEB_FETCH("web_fetch_20250910", "web_fetch"),
        CODE_EXECUTION("code_execution_20250522", "code_execution"),
        BASH("bash_20250124", "bash"),
        TEXT_EDITOR("text_editor_20250124", "str_replace_based_edit_tool"),
        TOOL_SEARCH("tool_search_tool_20250917", "tool_search_tool"),
        COMPUTER("computer_20250124", "computer"),
        MEMORY("memory_20250818", "memory"),
        ADVISOR("advisor_20250801", "advisor");

        private final String wireType;
        private final String defaultName;

        Kind(String wireType, String defaultName) {
            this.wireType = wireType;
            this.defaultName = defaultName;
        }

        @NonNull
        public String wireType() {
            return wireType;
        }

        @NonNull
        public String defaultName() {
            return defaultName;
        }
    }

    private final Kind kind;
    private final String name;
    private final JSONObject options;

    private YoMessageServerTool(Kind kind, String name, JSONObject options) {
        this.kind = kind;
        this.name = name;
        this.options = options;
    }

    @NonNull
    public static YoMessageServerTool of(@NonNull Kind kind) {
        return of(kind, null);
    }

    @NonNull
    public static YoMessageServerTool of(@NonNull Kind kind, @Nullable JSONObject options) {
        return of(kind, kind == null ? null : kind.defaultName(), options);
    }

    @NonNull
    public static YoMessageServerTool of(
            @NonNull Kind kind,
            @Nullable String name,
            @Nullable JSONObject options) {
        if (kind == null) {
            throw new IllegalArgumentException("server tool kind is required");
        }
        String resolved = name == null || name.isBlank() ? kind.defaultName() : name;
        return new YoMessageServerTool(kind, resolved, options == null ? new JSONObject() : copy(options));
    }

    @NonNull
    public Kind kind() {
        return kind;
    }

    @NonNull
    public String name() {
        return name;
    }

    @NonNull
    public String wireType() {
        return kind.wireType();
    }

    @NonNull
    public JSONObject options() {
        return copy(options);
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }
}
