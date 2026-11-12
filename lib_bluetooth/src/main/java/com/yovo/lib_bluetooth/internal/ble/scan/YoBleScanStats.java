package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 扫描会话统计：启动/发现/过期/失败/停止次数与上次扫描时长。
 *
 * <p>写入只发生在 state 线程；读取（{@link #snapshot()}）可能来自诊断线程，因此各计数保持
 * volatile 可见性，快照允许看到并发更新中的近似值。</p>
 *
 * <p>扫描时长只由单调时钟计算：类型内固定使用纳秒单调时钟（{@link System#nanoTime()} 语义，
 * 测试可注入等价来源），不使用墙钟差值——墙钟会因校时/用户改时间回拨，差值可能为负。单调时钟下
 * {@code now - activeStart} 恒非负，因此不存在「钳到 0」这一步。</p>
 */
public final class YoBleScanStats {

    /** 扫描会话统计快照（供诊断与 UI 读）。 */
    public record Stats(long starts,
                        long founds,
                        long losts,
                        long fails,
                        long stops,
                        long lastDurationMs) {
    }

    /** 纳秒单调时钟；语义与 {@link System#nanoTime()} 相同（只做差值，不作时间点）。 */
    @NonNull
    private final LongSupplier monotonicNanos;

    private volatile long starts;
    private volatile long founds;
    private volatile long losts;
    private volatile long fails;
    private volatile long stops;
    private volatile long lastDurationMillis;
    /** 当前会话的单调起点（纳秒）；仅 state 线程读写。 */
    private long activeStartNanos;
    /** 是否存在「已 recordStart、尚未 recordStop」的会话；仅 state 线程读写。 */
    private boolean sessionActive;

    YoBleScanStats() {
        this(System::nanoTime);
    }

    YoBleScanStats(@NonNull LongSupplier monotonicNanos) {
        this.monotonicNanos = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
    }

    void recordStart() {
        starts++;
        activeStartNanos = monotonicNanos.getAsLong();
        sessionActive = true;
    }

    void recordFounds(int count) {
        founds += count;
    }

    void recordLosts(int count) {
        losts += count;
    }

    void recordFail() {
        fails++;
    }

    /**
     * 结束当前会话并记录其时长。
     *
     * @throws IllegalStateException 没有对应的 {@link #recordStart()}：时长无起点可算，
     *                               按编程不变量违约抛出，而不是静默记 0
     */
    void recordStop() {
        if (!sessionActive) {
            throw new IllegalStateException("recordStop() requires a preceding recordStart()");
        }
        stops++;
        long elapsedNanos = monotonicNanos.getAsLong() - activeStartNanos;
        lastDurationMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        sessionActive = false;
    }

    @NonNull
    Stats snapshot() {
        return new Stats(starts, founds, losts, fails, stops, lastDurationMillis);
    }
}
