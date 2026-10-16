package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Responses 输入消息里的一块内容。{@code image_url} 是字符串。
 */
public final class YoResponseContentPart {

    public enum Kind {
        INPUT_TEXT,
        INPUT_IMAGE,
        INPUT_FILE
    }

    private final Kind kind;
    @Nullable
    private final String text;
    @Nullable
    private final String imageUrl;
    @Nullable
    private final String detail;
    @Nullable
    private final String fileId;
    @Nullable
    private final String fileData;
    @Nullable
    private final String fileUrl;
    @Nullable
    private final String filename;

    private YoResponseContentPart(
            Kind kind,
            @Nullable String text,
            @Nullable String imageUrl,
            @Nullable String detail,
            @Nullable String fileId,
            @Nullable String fileData,
            @Nullable String fileUrl,
            @Nullable String filename) {
        this.kind = kind;
        this.text = text;
        this.imageUrl = imageUrl;
        this.detail = detail;
        this.fileId = fileId;
        this.fileData = fileData;
        this.fileUrl = fileUrl;
        this.filename = filename;
    }

    @NonNull
    public static YoResponseContentPart text(@NonNull String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        return new YoResponseContentPart(Kind.INPUT_TEXT, text, null, null, null, null, null, null);
    }

    @NonNull
    public static YoResponseContentPart imageUrl(@NonNull String imageUrl) {
        return imageUrl(imageUrl, null);
    }

    @NonNull
    public static YoResponseContentPart imageUrl(@NonNull String imageUrl, @Nullable String detail) {
        return new YoResponseContentPart(
                Kind.INPUT_IMAGE,
                null,
                httpOrDataUrl(imageUrl),
                imageDetail(detail),
                null,
                null,
                null,
                null);
    }

    @NonNull
    public static YoResponseContentPart imageFile(@NonNull String fileId) {
        return new YoResponseContentPart(
                Kind.INPUT_IMAGE, null, null, null, required(fileId, "fileId"), null, null, null);
    }

    @NonNull
    public static YoResponseContentPart inputFileId(@NonNull String fileId) {
        return inputFile(fileId, null, null, null, null);
    }

    @NonNull
    public static YoResponseContentPart inputFileData(@NonNull String fileData, @NonNull String filename) {
        return inputFile(null, fileData, null, filename, null);
    }

    @NonNull
    public static YoResponseContentPart inputFileUrl(@NonNull String fileUrl) {
        return inputFile(null, null, fileUrl, null, null);
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
    public String imageUrl() {
        return imageUrl;
    }

    @Nullable
    public String detail() {
        return detail;
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
    public String fileUrl() {
        return fileUrl;
    }

    @Nullable
    public String filename() {
        return filename;
    }

    private static YoResponseContentPart inputFile(
            @Nullable String fileId,
            @Nullable String fileData,
            @Nullable String fileUrl,
            @Nullable String filename,
            @Nullable String detail) {
        String id = blankToNull(fileId);
        String data = blankToNull(fileData);
        String url = blankToNull(fileUrl);
        int present = (id == null ? 0 : 1) + (data == null ? 0 : 1) + (url == null ? 0 : 1);
        if (present != 1) {
            throw new IllegalArgumentException("input file requires exactly one of fileId, fileData, or fileUrl");
        }
        if (data != null && blankToNull(filename) == null) {
            throw new IllegalArgumentException("filename is required with fileData");
        }
        if (url != null && !url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException("file url must be an http(s) URL");
        }
        return new YoResponseContentPart(
                Kind.INPUT_FILE, null, null, fileDetail(detail), id, data, url, blankToNull(filename));
    }

    @NonNull
    private static String httpOrDataUrl(@NonNull String url) {
        String value = required(url, "image url");
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
    private static String fileDetail(@Nullable String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return switch (detail.trim().toLowerCase()) {
            case "low", "high", "auto" -> detail.trim().toLowerCase();
            default -> throw new IllegalArgumentException("file detail must be low, high, or auto");
        };
    }

    @NonNull
    private static String required(@Nullable String value, @NonNull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
