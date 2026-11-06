package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Chat Completions 的一条内容块。形状跟官方 user message content part 一致。
 *
 * <p>{@code cacheMode}/{@code cacheTtl} 是“提示缓存断点”的语义提示：OpenAI 编码为
 * {@code prompt_cache_breakpoint}，Anthropic 编码为 {@code cache_control}，其它方言可忽略。
 * 不把缓存断点做成独立 Kind，是因为它必须附着在某个内容块上，独立块没有内容可缓存。</p>
 */
public final class YoChatContentPart {

    public enum Kind {
        TEXT,
        IMAGE_URL,
        VIDEO_URL,
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
    private final String fileUrl;
    @Nullable
    private final String filename;
    @Nullable
    private final Double fps;
    @Nullable
    private final String cacheMode;
    @Nullable
    private final String cacheTtl;

    private YoChatContentPart(
            Kind kind,
            @Nullable String text,
            @Nullable String detail,
            @Nullable String audioFormat,
            @Nullable String fileId,
            @Nullable String fileData,
            @Nullable String fileUrl,
            @Nullable String filename,
            @Nullable Double fps,
            @Nullable String cacheMode,
            @Nullable String cacheTtl) {
        this.kind = kind;
        this.text = text;
        this.detail = detail;
        this.audioFormat = audioFormat;
        this.fileId = fileId;
        this.fileData = fileData;
        this.fileUrl = fileUrl;
        this.filename = filename;
        this.fps = fps;
        this.cacheMode = cacheMode;
        this.cacheTtl = cacheTtl;
    }

    @NonNull
    public static YoChatContentPart text(@NonNull String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        return new YoChatContentPart(Kind.TEXT, text, null, null, null, null, null, null, null, null, null);
    }

    @NonNull
    public static YoChatContentPart imageUrl(@NonNull String url) {
        return imageUrl(url, null);
    }

    @NonNull
    public static YoChatContentPart imageUrl(@NonNull String url, @Nullable String detail) {
        return new YoChatContentPart(
                Kind.IMAGE_URL, imageUrlValue(url), imageDetail(detail), null,
                null, null, null, null, null, null, null);
    }

    /**
     * 视频输入。{@code fps} 是抽帧频率，取值范围 [0.1, 10]，默认 2.0。
     */
    @NonNull
    public static YoChatContentPart videoUrl(@NonNull String url, @Nullable Double fps) {
        if (url.isBlank()) {
            throw new IllegalArgumentException("video url is required");
        }
        String value = url.trim();
        if (!value.startsWith("https://") && !value.startsWith("http://") && !value.startsWith("data:video/")) {
            throw new IllegalArgumentException("video url must be an http(s) URL or data:video URL");
        }
        if (fps != null && (fps < 0.1 || fps > 10.0)) {
            throw new IllegalArgumentException("fps must be between 0.1 and 10");
        }
        return new YoChatContentPart(
                Kind.VIDEO_URL, value, null, null,
                null, null, null, null, fps == null ? 2.0 : fps, null, null);
    }

    @NonNull
    public static YoChatContentPart inputAudio(@NonNull String data, @NonNull String format) {
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("audio data is required");
        }
        String normalized = format == null ? "" : format.trim().toLowerCase();
        if (!normalized.equals("wav") && !normalized.equals("mp3")
                && !normalized.equals("flac") && !normalized.equals("opus")
                && !normalized.equals("pcm16")) {
            throw new IllegalArgumentException("audio format must be wav, mp3, flac, opus, or pcm16");
        }
        return new YoChatContentPart(Kind.INPUT_AUDIO, data.trim(), null, normalized, null, null, null, null, null, null, null);
    }

    @NonNull
    public static YoChatContentPart fileId(@NonNull String fileId) {
        return file(fileId, null, null, null);
    }

    @NonNull
    public static YoChatContentPart fileData(@NonNull String fileData, @NonNull String filename) {
        return file(null, fileData, null, filename);
    }

    @NonNull
    public static YoChatContentPart fileUrl(@NonNull String fileUrl) {
        return file(null, null, fileUrl, null);
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
    public String fileUrl() {
        return fileUrl;
    }

    @Nullable
    public String filename() {
        return filename;
    }

    @Nullable
    public Double fps() {
        return fps;
    }

    @Nullable
    public String cacheMode() {
        return cacheMode;
    }

    @Nullable
    public String cacheTtl() {
        return cacheTtl;
    }

    /**
     * 给当前内容块加一个提示缓存断点，返回新对象（不可变）。
     *
     * @param mode {@code explicit} / {@code ephemeral} / {@code auto}
     * @param ttl  {@code 30m} / {@code 12h} / {@code 5m} / {@code 1h}，可为 null
     */
    @NonNull
    public YoChatContentPart withCache(@NonNull String mode, @Nullable String ttl) {
        String normalizedMode = mode == null ? "" : mode.trim().toLowerCase();
        if (!normalizedMode.equals("explicit") && !normalizedMode.equals("ephemeral")
                && !normalizedMode.equals("auto")) {
            throw new IllegalArgumentException("cache mode must be explicit, ephemeral, or auto");
        }
        String normalizedTtl = ttl == null ? null : ttl.trim().toLowerCase();
        if (normalizedTtl != null && !normalizedTtl.equals("30m") && !normalizedTtl.equals("12h")
                && !normalizedTtl.equals("5m") && !normalizedTtl.equals("1h")) {
            throw new IllegalArgumentException("cache ttl must be 30m, 12h, 5m, or 1h");
        }
        return new YoChatContentPart(
                kind, text, detail, audioFormat, fileId, fileData, fileUrl, filename, fps,
                normalizedMode, normalizedTtl);
    }

    private static YoChatContentPart file(
            @Nullable String fileId,
            @Nullable String fileData,
            @Nullable String fileUrl,
            @Nullable String filename) {
        String id = blankToNull(fileId);
        String data = blankToNull(fileData);
        String url = blankToNull(fileUrl);
        String name = blankToNull(filename);
        int present = (id == null ? 0 : 1) + (data == null ? 0 : 1) + (url == null ? 0 : 1);
        if (present != 1) {
            throw new IllegalArgumentException("file requires exactly one of fileId, fileData, or fileUrl");
        }
        if (data != null && name == null) {
            throw new IllegalArgumentException("filename is required with fileData");
        }
        if (url != null && !url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException("file url must be an http(s) URL");
        }
        return new YoChatContentPart(Kind.FILE, null, null, null, id, data, url, name, null, null, null);
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
