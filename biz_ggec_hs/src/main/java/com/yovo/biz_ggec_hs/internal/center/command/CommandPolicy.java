package com.yovo.biz_ggec_hs.internal.center.protocol;

/**
 * 具体命令的调度语义。
 */
final class CommandPolicy {

    enum Kind {
        GET,
        SET,
        ACTION
    }

    enum MergeMode {
        NONE,
        LATEST_WINS,
        SINGLE_FLIGHT
    }

    private final Kind kind;
    private final MergeMode mergeMode;
    private final String semanticKey;
    private final String valueFingerprint;
    private final long cacheTtlMs;
    /** 0 表示未声明，调度器沿用 {@link Command#getTimeoutMs()}。 */
    private final long responseTimeoutMs;

    CommandPolicy(Kind kind,
                  MergeMode mergeMode,
                  String semanticKey,
                  String valueFingerprint,
                  long cacheTtlMs,
                  long responseTimeoutMs) {
        this.kind = kind;
        this.mergeMode = mergeMode;
        this.semanticKey = semanticKey;
        this.valueFingerprint = valueFingerprint;
        this.cacheTtlMs = cacheTtlMs;
        this.responseTimeoutMs = Math.max(0L, responseTimeoutMs);
    }

    Kind getKind() {
        return kind;
    }

    MergeMode getMergeMode() {
        return mergeMode;
    }

    String getSemanticKey() {
        return semanticKey;
    }

    String getValueFingerprint() {
        return valueFingerprint;
    }

    long getCacheTtlMs() {
        return cacheTtlMs;
    }

    long getResponseTimeoutMs() {
        return responseTimeoutMs;
    }
}
