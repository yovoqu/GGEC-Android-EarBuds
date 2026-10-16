package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Messages 的一条内容块。图片源的媒体类型和载荷分开存放。
 */
public final class YoMessageContent {

    public enum Type {
        TEXT,
        IMAGE,
        DOCUMENT,
        TOOL_USE,
        TOOL_RESULT,
        THINKING,
        REDACTED_THINKING
    }

    private final Type type;
    @Nullable
    private final String text;
    @Nullable
    private final String id;
    @Nullable
    private final String name;
    @Nullable
    private final JSONObject input;
    @Nullable
    private final String signature;
    @Nullable
    private final String sourceType;
    @Nullable
    private final String mediaType;
    @Nullable
    private final String payload;

    private YoMessageContent(
            Type type,
            @Nullable String text,
            @Nullable String id,
            @Nullable String name,
            @Nullable JSONObject input,
            @Nullable String signature,
            @Nullable String sourceType,
            @Nullable String mediaType,
            @Nullable String payload) {
        this.type = type;
        this.text = text;
        this.id = id;
        this.name = name;
        this.input = input;
        this.signature = signature;
        this.sourceType = sourceType;
        this.mediaType = mediaType;
        this.payload = payload;
    }

    @NonNull
    public static YoMessageContent text(@NonNull String text) {
        return new YoMessageContent(Type.TEXT, text, null, null, null, null, null, null, null);
    }

    @NonNull
    public static YoMessageContent imageUrl(@NonNull String url) {
        return source(Type.IMAGE, "url", null, httpUrl(url));
    }

    @NonNull
    public static YoMessageContent imageBase64(@NonNull String mediaType, @NonNull String data) {
        return source(Type.IMAGE, "base64", imageMediaType(mediaType), base64Payload(data));
    }

    @NonNull
    public static YoMessageContent imageFile(@NonNull String fileId) {
        return source(Type.IMAGE, "file", null, required(fileId, "fileId"));
    }

    @NonNull
    public static YoMessageContent documentUrl(@NonNull String url) {
        return source(Type.DOCUMENT, "url", null, httpUrl(url));
    }

    @NonNull
    public static YoMessageContent documentBase64(@NonNull String mediaType, @NonNull String data) {
        return source(Type.DOCUMENT, "base64", documentMediaType(mediaType), base64Payload(data));
    }

    @NonNull
    public static YoMessageContent documentFile(@NonNull String fileId) {
        return source(Type.DOCUMENT, "file", null, required(fileId, "fileId"));
    }

    @NonNull
    public static YoMessageContent documentText(@NonNull String text) {
        return source(Type.DOCUMENT, "text", "text/plain", required(text, "text"));
    }

    @NonNull
    public static YoMessageContent toolUse(@NonNull String id, @NonNull String name, @NonNull JSONObject input) {
        if (id.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("tool use id and name are required");
        }
        return new YoMessageContent(Type.TOOL_USE, null, id, name, copy(input), null, null, null, null);
    }

    @NonNull
    public static YoMessageContent toolResult(@NonNull String toolUseId, @NonNull String content) {
        if (toolUseId.isBlank()) {
            throw new IllegalArgumentException("toolUseId is required");
        }
        return new YoMessageContent(Type.TOOL_RESULT, content, toolUseId, null, null, null, null, null, null);
    }

    @NonNull
    public static YoMessageContent thinking(@NonNull String thinking) {
        return thinking(thinking, null);
    }

    @NonNull
    public static YoMessageContent thinking(@NonNull String thinking, @Nullable String signature) {
        return new YoMessageContent(Type.THINKING, thinking, null, null, null, signature, null, null, null);
    }

    @NonNull
    public static YoMessageContent redactedThinking(@NonNull String data) {
        return new YoMessageContent(
                Type.REDACTED_THINKING, null, null, null, null, null, null, null, required(data, "data"));
    }

    @NonNull
    public Type type() {
        return type;
    }

    @Nullable
    public String text() {
        return text;
    }

    @Nullable
    public String id() {
        return id;
    }

    @Nullable
    public String name() {
        return name;
    }

    @Nullable
    public String signature() {
        return signature;
    }

    @Nullable
    public String sourceType() {
        return sourceType;
    }

    @Nullable
    public String mediaType() {
        return mediaType;
    }

    @Nullable
    public String payload() {
        return payload;
    }

    @Nullable
    public JSONObject input() {
        return input == null ? null : copy(input);
    }

    private static YoMessageContent source(Type type, String sourceType, @Nullable String mediaType, String payload) {
        return new YoMessageContent(type, null, null, null, null, null, sourceType, mediaType, payload);
    }

    @NonNull
    private static String httpUrl(@NonNull String url) {
        String value = required(url, "url");
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            throw new IllegalArgumentException("url must be an http(s) URL");
        }
        return value;
    }

    @NonNull
    private static String imageMediaType(@NonNull String mediaType) {
        String value = required(mediaType, "mediaType").toLowerCase();
        return switch (value) {
            case "image/jpeg", "image/png", "image/gif", "image/webp" -> value;
            default -> throw new IllegalArgumentException("mediaType must be image/jpeg, image/png, image/gif, or image/webp");
        };
    }

    @NonNull
    private static String documentMediaType(@NonNull String mediaType) {
        String value = required(mediaType, "mediaType").toLowerCase();
        if (!value.equals("application/pdf") && !value.equals("text/plain")) {
            throw new IllegalArgumentException("document mediaType must be application/pdf or text/plain");
        }
        return value;
    }

    @NonNull
    private static String base64Payload(@NonNull String data) {
        String value = required(data, "data");
        if (value.startsWith("data:")) {
            throw new IllegalArgumentException("data must be the base64 payload, with mediaType set separately");
        }
        return value;
    }

    @NonNull
    private static String required(@Nullable String value, @NonNull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return new JSONObject(json.toString());
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }
}
