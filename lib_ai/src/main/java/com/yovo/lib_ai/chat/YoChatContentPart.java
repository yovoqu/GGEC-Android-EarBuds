package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Chat Completions 的一条内容块。形状跟官方 user message content part 一致。
 */
public final class YoChatContentPart {

    public enum Kind {
        TEXT,
        IMAGE_URL,
        INPUT_AUDIO,
        FILE
    }

    private final Kind kind;
    @Nullable
    private final String text;
    @Nullable
    private final String detail;
    @Nullable
    private final String audioFormat;
    @Nullable
    private final String fileId;
    @Nullable
    private final String fileData;
    @Nullable
    private final String filename;

    private YoChatContentPart(
            Kind kind,
            @Nullable String text,
            @Nullable String detail,
            @Nullable String audioFormat,
            @Nullable String fileId,
            @Nullable String fileData,
            @Nullable String filename) {
        this.kind = kind;
        this.text = text;
        this.detail = detail;
        this.audioFormat = audioFormat;
        this.fileId = fileId;
        this.fileData = fileData;
        this.filename = filename;
    }

    @NonNull
    public static YoChatContentPart text(@NonNull String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        return new YoChatContentPart(Kind.TEXT, text, null, null, null, null, null);
    }

    @NonNull
    public static YoChatContentPart imageUrl(@NonNull String url) {
        return imageUrl(url, null);
    }

    @NonNull
    public static YoChatContentPart imageUrl(@NonNull String url, @Nullable String detail) {
        return new YoChatContentPart(Kind.IMAGE_URL, imageUrlValue(url), imageDetail(detail), null, null, null, null);
    }

    @NonNull
    public static YoChatContentPart inputAudio(@NonNull String data, @NonNull String format) {
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("audio data is required");
        }
        String normalized = format == null ? "" : format.trim().toLowerCase();
        if (!normalized.equals("wav") && !normalized.equals("mp3")) {
            throw new IllegalArgumentException("audio format must be wav or mp3");
        }
        return new YoChatContentPart(Kind.INPUT_AUDIO, data.trim(), null, normalized, null, null, null);
    }

    @NonNull
    public static YoChatContentPart fileId(@NonNull String fileId) {
        return file(fileId, null, null);
    }

    @NonNull
    public static YoChatContentPart fileData(@NonNull String fileData, @NonNull String filename) {
        return file(null, fileData, filename);
    }

    @NonNull
    public Kind kind() {
        return kind;
    }

    @Nullable
    public String text() {
        return text;
    }

    @Nullable
    public String detail() {
        return detail;
    }

    @Nullable
    public String audioFormat() {
        return audioFormat;
    }

    @Nullable
    public String fileId() {
        return fileId;
    }

    @Nullable
    public String fileData() {
        return fileData;
    }

    @Nullable
    public String filename() {
        return filename;
    }

    private static YoChatContentPart file(
            @Nullable String fileId,
            @Nullable String fileData,
            @Nullable String filename) {
        String id = blankToNull(fileId);
        String data = blankToNull(fileData);
        String name = blankToNull(filename);
        if ((id == null) == (data == null)) {
            throw new IllegalArgumentException("file requires fileId or fileData");
        }
        if (data != null && name == null) {
            throw new IllegalArgumentException("filename is required with fileData");
        }
        return new YoChatContentPart(Kind.FILE, null, null, null, id, data, name);
    }

    @NonNull
    private static String imageUrlValue(@NonNull String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("image url is required");
        }
        String value = url.trim();
        if (value.startsWith("data:")) {
            int semi = value.indexOf(';');
            int comma = value.indexOf(',');
            if (!value.regionMatches(true, 0, "data:image/", 0, "data:image/".length())
                    || semi < "data:image/".length()
                    || comma < semi
                    || !value.substring(semi, comma).equalsIgnoreCase(";base64")
                    || value.substring(comma + 1).isBlank()) {
                throw new IllegalArgumentException("image data url must be data:image/<type>;base64,<payload>");
            }
            switch (value.substring("data:image/".length(), semi).toLowerCase()) {
                case "jpeg", "jpg", "png", "webp", "gif" -> {
                    return value;
                }
                default -> throw new IllegalArgumentException("image type must be jpeg, jpg, png, webp, or gif");
            }
        }
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            throw new IllegalArgumentException("image url must be an http(s) URL or a data URL");
        }
        return value;
    }

    @Nullable
    private static String imageDetail(@Nullable String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return switch (detail.trim().toLowerCase()) {
            case "low", "high", "auto", "original" -> detail.trim().toLowerCase();
            default -> throw new IllegalArgumentException("image detail must be low, high, auto, or original");
        };
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
