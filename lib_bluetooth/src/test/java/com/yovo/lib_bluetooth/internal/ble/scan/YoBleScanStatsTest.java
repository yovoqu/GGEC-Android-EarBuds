package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link YoBleScanStats} 时长语义：只由注入的纳秒单调时钟计算差值，不做「钳到 0」；
 * 没有 start 的 stop 是编程不变量违约，必须抛出而不是静默记 0。
 */
public final class YoBleScanStatsTest {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

    @Test
    public void durationIsTheMonotonicDifference() {
        YoBleScanStats stats = new YoBleScanStats(nanos::get);
        stats.recordStart();
        nanos.addAndGet(250_000_000L);
        stats.recordStop();

        assertEquals(250L, stats.snapshot().lastDurationMs());
        assertEquals(1L, stats.snapshot().starts());
        assertEquals(1L, stats.snapshot().stops());
    }

    @Test
    public void zeroLengthSessionIsZeroNotOneMillisecond() {
        YoBleScanStats stats = new YoBleScanStats(nanos::get);
        stats.recordStart();
        stats.recordStop();

        assertEquals(0L, stats.snapshot().lastDurationMs());
    }

    @Test
    public void eachSessionMeasuresFromItsOwnStart() {
        YoBleScanStats stats = new YoBleScanStats(nanos::get);

        stats.recordStart();
        nanos.addAndGet(2_000_000_000L);
        stats.recordStop();
        assertEquals(2_000L, stats.snapshot().lastDurationMs());

        nanos.addAndGet(5_000_000_000L);
        stats.recordStart();
        nanos.addAndGet(125_000_000L);
        stats.recordStop();

        assertEquals(125L, stats.snapshot().lastDurationMs());
        assertEquals(2L, stats.snapshot().starts());
        assertEquals(2L, stats.snapshot().stops());
    }

    @Test
    public void recordStopWithoutStartIsAContractViolation() {
        YoBleScanStats stats = new YoBleScanStats(nanos::get);
        assertThrows(IllegalStateException.class, stats::recordStop);
    }

    @Test
    public void countersAccumulate() {
        YoBleScanStats stats = new YoBleScanStats(nanos::get);
        stats.recordStart();
        stats.recordFounds(2);
        stats.recordFounds(3);
        stats.recordLosts(1);
        stats.recordFail();
        stats.recordStop();

        YoBleScanStats.Stats snapshot = stats.snapshot();
        assertEquals(1L, snapshot.starts());
        assertEquals(5L, snapshot.founds());
        assertEquals(1L, snapshot.losts());
        assertEquals(1L, snapshot.fails());
        assertEquals(1L, snapshot.stops());
    }
}
