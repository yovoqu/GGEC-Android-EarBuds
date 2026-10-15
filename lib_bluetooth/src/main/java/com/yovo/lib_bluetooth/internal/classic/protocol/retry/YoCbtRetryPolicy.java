package com.yovo.lib_bluetooth.internal.classic.protocol.retry;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 命令重试策略，定义失败后的重试次数与间隔规则。
 */

public final class YoCbtRetryPolicy {
    private final int maxAttempts;
    private final long retryDelayMillis;

    public YoCbtRetryPolicy(int maxAttempts, long retryDelayMillis) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMillis = Math.max(0L, retryDelayMillis);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getRetryDelayMillis() {
        return retryDelayMillis;
    }

    public static YoCbtRetryPolicy none() {
        return new YoCbtRetryPolicy(1, 0L);
    }
}
