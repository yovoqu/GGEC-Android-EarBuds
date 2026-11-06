package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class YoResponseOutputItem {

    /**
     * 已知的 output item 类型。识别不了的类型归到 OTHER，raw 仍然完整保留。
     */
    public enum Kind {
        MESSAGE,
        FUNCTION_CALL,
        FUNCTION_CALL_OUTPUT,
        REASONING,
        WEB_SEARCH_CALL,
        FILE_SEARCH_CALL,
        CODE_INTERPRETER_CALL,
        COMPUTER_CALL,
        MCP_CALL,
        IMAGE_GENERATION_CALL,
        TOOL_SEARCH_CALL,
        OTHER
    }

    private final String type;
    private final Kind kind;
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
        this.kind = kindOf(type);
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
    public static Kind kindOf(@Nullable String type) {
        if (type == null) {
            return Kind.OTHER;
        }
        return switch (type) {
            case "message" -> Kind.MESSAGE;
            case "function_call" -> Kind.FUNCTION_CALL;
            case "function_call_output" -> Kind.FUNCTION_CALL_OUTPUT;
            case "reasoning" -> Kind.REASONING;
            case "web_search_call" -> Kind.WEB_SEARCH_CALL;
            case "file_search_call" -> Kind.FILE_SEARCH_CALL;
            case "code_interpreter_call" -> Kind.CODE_INTERPRETER_CALL;
            case "computer_call" -> Kind.COMPUTER_CALL;
            case "mcp_call" -> Kind.MCP_CALL;
            case "image_generation_call" -> Kind.IMAGE_GENERATION_CALL;
            case "tool_search_call" -> Kind.TOOL_SEARCH_CALL;
            default -> Kind.OTHER;
        };
    }

    @NonNull
    public String type() {
        return type;
    }

    @NonNull
    public Kind kind() {
        return kind;
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
