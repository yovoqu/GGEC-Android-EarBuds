package com.yovo.lib_bluetooth.internal.classic.protocol.retry;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 命令重试策略评估返回的决策（是否重试、延迟等）。
 */
public final class YoCbtRetryDecision {
    private final boolean shouldRetry;
    private final long delayMillis;
    private final String reason;

    private YoCbtRetryDecision(boolean shouldRetry, long delayMillis, @NonNull String reason) {
        this.shouldRetry = shouldRetry;
        this.delayMillis = delayMillis;
        this.reason = reason;
    }

    public boolean isShouldRetry() { return shouldRetry; }
    public long getDelayMillis() { return delayMillis; }
    @NonNull public String getReason() { return reason; }

    @NonNull
    public static YoCbtRetryDecision retry(long delayMillis) {
        return new YoCbtRetryDecision(true, delayMillis, "retry after " + delayMillis + "ms");
    }

    @NonNull
    public static YoCbtRetryDecision noRetry(@NonNull String reason) {
        return new YoCbtRetryDecision(false, 0, reason);
    }
}
