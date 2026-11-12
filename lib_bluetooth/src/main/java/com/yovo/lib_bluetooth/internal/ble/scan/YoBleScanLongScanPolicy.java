package com.yovo.lib_bluetooth.internal.ble.scan;

/**
 * 长扫重启策略：系统会在扫描超过 30 分钟后降级为 opportunistic，需在此之前主动重启。
 * 显式短超时（{@code 0 < timeout <= 重启间隔}）的会话不会长于该间隔，无需重启。
 */
final class YoBleScanLongScanPolicy {

    /** 主动重启前允许的最长连续扫描时长。 */
    static final long RESTART_INTERVAL_MS = 25L * 60L * 1000L;

    private YoBleScanLongScanPolicy() {
    }

    static boolean shouldArm(long timeoutMillis) {
        return !(timeoutMillis > 0L && timeoutMillis <= RESTART_INTERVAL_MS);
    }
}
