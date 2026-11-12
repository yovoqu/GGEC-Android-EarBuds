package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Android 7+「30 秒内最多 5 次」扫描限流滑窗单测：窗口内第 6 次被拒并给出剩余等待时间，窗口滑过后放行。
 *
 * <p>时钟由 {@link YoBleScanThrottleGuard} 的构造参数注入（与 {@code YoBleScanController} 生产路径
 * 复用同一个 {@code LongSupplier}），不新增任何生产钩子。</p>
 */
public final class YoBleScanThrottleGuardTest {

    private static final long WINDOW_MILLIS = 30_000L;
    private static final int MAX_STARTS = 5;

    private final AtomicLong clock = new AtomicLong(1_000L);

    @Test
    public void rejectsZeroWindowMillis() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new YoBleScanThrottleGuard(0L, MAX_STARTS, clock::get));
        assertTrue(error.getMessage().contains("windowMillis"));
        assertTrue(error.getMessage().contains("0"));
    }

    @Test
    public void rejectsZeroMaxStarts() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new YoBleScanThrottleGuard(WINDOW_MILLIS, 0, clock::get));
        assertTrue(error.getMessage().contains("maxStarts"));
        assertTrue(error.getMessage().contains("0"));
    }

    @Test
    public void rejectsShortWindowThatOldClampWouldHaveSilentlyWidened() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new YoBleScanThrottleGuard(-1L, MAX_STARTS, clock::get));
        assertTrue(error.getMessage().contains("windowMillis"));

        // 旧实现的 1000ms 下限钳位必须消失：小于旧下限但合法的窗口必须原样生效。
        YoBleScanThrottleGuard guard = new YoBleScanThrottleGuard(10L, 1, clock::get);
        guard.recordScanStarted();
        assertEquals("10ms 窗口内必须按真实窗口等待", 10L, guard.requiredDelayMillis());
        clock.addAndGet(10L);
        assertEquals("窗口期满必须放行", 0L, guard.requiredDelayMillis());
    }

    @Test
    public void rejectsSixthStartInsideWindowAndReportsRemainingWait() {
        YoBleScanThrottleGuard guard = newGuard();
        for (int i = 1; i <= MAX_STARTS; i++) {
            assertEquals("窗口内第 " + i + " 次必须放行", 0L, guard.requiredDelayMillis());
            guard.recordScanStarted();
        }

        assertEquals("窗口内第 6 次必须被拒，并给出整窗等待时间",
                WINDOW_MILLIS, guard.requiredDelayMillis());

        clock.addAndGet(5_000L);
        assertEquals("等待时间必须随滑窗推进递减",
                WINDOW_MILLIS - 5_000L, guard.requiredDelayMillis());

        clock.addAndGet(WINDOW_MILLIS - 5_000L - 1L);
        assertEquals("窗口期满前一毫秒仍被拒，且等到 1ms", 1L, guard.requiredDelayMillis());
    }

    @Test
    public void allowsStartAgainOnceTheWindowSlidesPastTheOldestStart() {
        YoBleScanThrottleGuard guard = newGuard();
        for (int i = 1; i <= MAX_STARTS; i++) {
            guard.requiredDelayMillis();
            guard.recordScanStarted();
            clock.addAndGet(1_000L);
        }
        assertTrue("5 次占满滑窗后必须给出等待", guard.requiredDelayMillis() > 0L);

        // 最老一次（1000ms）恰好出窗：窗口滑过即放行。
        clock.set(1_000L + WINDOW_MILLIS);
        assertEquals("窗口滑过后必须放行", 0L, guard.requiredDelayMillis());

        guard.recordScanStarted();
        assertTrue("放行后重新占满滑窗必须再次给出等待", guard.requiredDelayMillis() > 0L);
    }

    private YoBleScanThrottleGuard newGuard() {
        return new YoBleScanThrottleGuard(WINDOW_MILLIS, MAX_STARTS, clock::get);
    }
}
