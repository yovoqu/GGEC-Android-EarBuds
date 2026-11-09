package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * Android 7+ BLE 扫描 30 秒内最多 5 次系统限流滑窗防护器（对标 RxAndroidBle ScanPreconditionsVerifierApi24）。
 */
public final class YoBleScanThrottleGuard {

    private final long windowMillis;
    private final int maxStarts;
    @NonNull
    private final LongSupplier clockMillis;
    private final Deque<Long> startTimestamps = new ArrayDeque<>();

    public YoBleScanThrottleGuard(long windowMillis,
                                  int maxStarts,
                                  @NonNull LongSupplier clockMillis) {
        this.windowMillis = Math.max(1_000L, windowMillis);
        this.maxStarts = Math.max(1, maxStarts);
        this.clockMillis = clockMillis;
    }

    /**
     * 检查当前时刻是否允许启动扫描；返回 0 表示立即放行，>0 表示需等待的毫秒数。
     */
    public synchronized long requiredDelayMillis() {
        long now = clockMillis.getAsLong();
        pruneExpired(now);
        if (startTimestamps.size() < maxStarts) {
            return 0L;
        }
        Long oldest = startTimestamps.peekFirst();
        if (oldest == null) {
            return 0L;
        }
        long unlockAt = oldest + windowMillis;
        return Math.max(1L, unlockAt - now);
    }

    /** 记录一次真实发起底层扫描的时间戳。 */
    public synchronized void recordScanStarted() {
        long now = clockMillis.getAsLong();
        pruneExpired(now);
        startTimestamps.addLast(now);
        while (startTimestamps.size() > maxStarts) {
            startTimestamps.removeFirst();
        }
    }

    public synchronized int recentStartCount() {
        pruneExpired(clockMillis.getAsLong());
        return startTimestamps.size();
    }

    public synchronized void reset() {
        startTimestamps.clear();
    }

    private void pruneExpired(long now) {
        while (!startTimestamps.isEmpty()) {
            long head = startTimestamps.peekFirst();
            if (now - head >= windowMillis) {
                startTimestamps.removeFirst();
            } else {
                break;
            }
        }
    }
}
