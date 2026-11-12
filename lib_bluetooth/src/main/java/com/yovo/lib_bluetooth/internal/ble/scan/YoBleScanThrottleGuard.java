package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Android 7+ BLE 扫描 30 秒内最多 5 次系统限流滑窗防护器（对标 RxAndroidBle ScanPreconditionsVerifierApi24）。
 *
 * <p>参数是代码常量，非法即编程错误：{@code windowMillis} 必须为正、{@code maxStarts} 必须为正，
 * 构造点直接抛 {@link IllegalArgumentException}，不做钳位。</p>
 */
public final class YoBleScanThrottleGuard {

    private final long windowMillis;
    private final int maxStarts;
    @NonNull
    private final LongSupplier clockMillis;
    private final Deque<Long> startTimestamps = new ArrayDeque<>();

    /**
     * @param windowMillis 滑窗长度，合法域 {@code > 0}
     * @param maxStarts    窗口内允许的启动次数，合法域 {@code > 0}
     * @param clockMillis  与扫描控制器共用的时钟（毫秒），不可为 null
     * @throws IllegalArgumentException {@code windowMillis <= 0} 或 {@code maxStarts <= 0}
     */
    public YoBleScanThrottleGuard(long windowMillis,
                                  int maxStarts,
                                  @NonNull LongSupplier clockMillis) {
        if (windowMillis <= 0L) {
            throw new IllegalArgumentException(
                    "windowMillis must be > 0, but was " + windowMillis);
        }
        if (maxStarts <= 0) {
            throw new IllegalArgumentException(
                    "maxStarts must be > 0, but was " + maxStarts);
        }
        this.windowMillis = windowMillis;
        this.maxStarts = maxStarts;
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    }

    /**
     * 检查当前时刻是否允许启动扫描；返回 0 表示立即放行，>0 表示需等待的毫秒数。
     *
     * <p>等待时长由「最老一次启动 + 窗口」与当前时刻的差直接给出，不做钳位：窗口已滑过时该差为
     * 非正数，即「无需等待」，用显式判定返回 0 表达，而不是把非正差值改成 1ms。</p>
     */
    public synchronized long requiredDelayMillis() {
        long now = clockMillis.getAsLong();
        if (startTimestamps.size() < maxStarts) {
            return 0L;
        }
        // size >= maxStarts >= 1，队列非空；过期记录由 recordScanStarted 的 pruneExpired 维护，
        // 因此这里自行判定「最老一次是否已满窗口」，不能依赖钳位。
        long unlockAt = startTimestamps.peekFirst() + windowMillis;
        if (unlockAt <= now) {
            return 0L;
        }
        return unlockAt - now;
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

    /**
     * 当前窗口内已发起的启动次数（窗口外的记录先被剔除）。
     *
     * <p>这是对外的可观测事实：健康度用它暴露"平台已经接受了多少次启动"，
     * 调用方据此把静默限流变成可见状态。</p>
     */
    public synchronized int startsInWindow() {
        pruneExpired(clockMillis.getAsLong());
        return startTimestamps.size();
    }

    /**
     * 现在再发起一次启动是否会超出窗口上限。
     *
     * <p>判定与 {@link #requiredDelayMillis()} 同源（都基于窗口内的记录数），但语义不同：
     * 本方法供"直接拒绝、不发起平台扫描"的硬守卫使用。</p>
     */
    public synchronized boolean wouldExceed() {
        return startsInWindow() >= maxStarts;
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
