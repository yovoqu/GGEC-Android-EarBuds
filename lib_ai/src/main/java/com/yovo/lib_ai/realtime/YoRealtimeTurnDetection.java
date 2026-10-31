package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 谁来判断「这句话说完了」。
 *
 * <p>配置里留空表示沿用该方言的默认：服务端 VAD，并且由服务端自动开口回答。
 * 要改静音时长、换成语义 VAD，或者改成按住说话，才构造这个对象。</p>
 */
public final class YoRealtimeTurnDetection {

    /** 断句算法。关掉断句时没有 kind，客户端自己提交缓冲区。 */
    public enum Kind {
        SERVER_VAD("server_vad"),
        SEMANTIC_VAD("semantic_vad");

        @NonNull
        private final String wireName;

        Kind(@NonNull String wireName) {
            this.wireName = wireName;
        }

        @NonNull
        public String wireName() {
            return wireName;
        }
    }

    private final boolean disabled;
    @Nullable
    private final Kind kind;
    @Nullable
    private final Double threshold;
    @Nullable
    private final Integer prefixPaddingMs;
    @Nullable
    private final Integer silenceDurationMs;
    @Nullable
    private final String eagerness;
    @Nullable
    private final Boolean createResponse;
    @Nullable
    private final Boolean interruptResponse;

    private YoRealtimeTurnDetection(
            boolean disabled,
            @Nullable Kind kind,
            @Nullable Double threshold,
            @Nullable Integer prefixPaddingMs,
            @Nullable Integer silenceDurationMs,
            @Nullable String eagerness,
            @Nullable Boolean createResponse,
            @Nullable Boolean interruptResponse) {
        this.disabled = disabled;
        this.kind = kind;
        this.threshold = threshold;
        this.prefixPaddingMs = prefixPaddingMs;
        this.silenceDurationMs = silenceDurationMs;
        this.eagerness = eagerness;
        this.createResponse = createResponse;
        this.interruptResponse = interruptResponse;
    }

    /**
     * 按能量切句。没填的可选参数不会出现在 JSON 里，避免把服务端默认值盖成 null。
     */
    @NonNull
    public static YoRealtimeTurnDetection serverVad() {
        return new YoRealtimeTurnDetection(false, Kind.SERVER_VAD, null, null, null, null, null, null);
    }

    /**
     * 能量 VAD，只写调用方明确要改的项。
     */
    @NonNull
    public static YoRealtimeTurnDetection serverVad(
            @Nullable Double threshold,
            @Nullable Integer prefixPaddingMs,
            @Nullable Integer silenceDurationMs,
            @Nullable Boolean createResponse,
            @Nullable Boolean interruptResponse) {
        return new YoRealtimeTurnDetection(
                false,
                Kind.SERVER_VAD,
                threshold,
                prefixPaddingMs,
                silenceDurationMs,
                null,
                createResponse,
                interruptResponse);
    }

    /**
     * 按语义判断停顿是「还在想」还是「说完了」。{@code eagerness} 为空则用服务端默认。
     */
    @NonNull
    public static YoRealtimeTurnDetection semanticVad(@Nullable String eagerness) {
        String cleaned = eagerness == null || eagerness.isBlank() ? null : eagerness.trim();
        return new YoRealtimeTurnDetection(false, Kind.SEMANTIC_VAD, null, null, null, cleaned, null, null);
    }

    /**
     * 关掉服务端断句。之后必须由客户端 {@link YoRealtimeLink#commitInput()}，
     * 否则模型永远等不到一句的结尾。
     */
    @NonNull
    public static YoRealtimeTurnDetection disabled() {
        return new YoRealtimeTurnDetection(true, null, null, null, null, null, null, null);
    }

    public boolean isDisabled() {
        return disabled;
    }

    @Nullable
    public Kind kind() {
        return kind;
    }

    @Nullable
    public Double threshold() {
        return threshold;
    }

    @Nullable
    public Integer prefixPaddingMs() {
        return prefixPaddingMs;
    }

    @Nullable
    public Integer silenceDurationMs() {
        return silenceDurationMs;
    }

    @Nullable
    public String eagerness() {
        return eagerness;
    }

    @Nullable
    public Boolean createResponse() {
        return createResponse;
    }

    @Nullable
    public Boolean interruptResponse() {
        return interruptResponse;
    }
}
