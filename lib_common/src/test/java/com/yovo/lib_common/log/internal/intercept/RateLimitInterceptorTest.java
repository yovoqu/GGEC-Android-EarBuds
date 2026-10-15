package com.yovo.lib_common.log.internal.intercept;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogRecord;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

public final class RateLimitInterceptorTest {

    @Test
    public void overLimit_dropsNonUrgentButKeepsError() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(2, 60);

        assertNotNull(interceptor.intercept(record(LogLevel.DEBUG)));
        assertNotNull(interceptor.intercept(record(LogLevel.INFO)));
        assertNull(interceptor.intercept(record(LogLevel.WARN)));
        assertNotNull(interceptor.intercept(record(LogLevel.ERROR)));
        assertNotNull(interceptor.intercept(record(LogLevel.ASSERT)));
        assertNull(interceptor.intercept(record(LogLevel.INFO)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLimits_rejected() {
        new RateLimitInterceptor(0, 10);
    }

    @Test
    public void windowReset_allowsNewBurst() {
        AtomicLong now = new AtomicLong(0L);
        RateLimitInterceptor interceptor =
                new RateLimitInterceptor(1, 60, now::get);

        assertNotNull(interceptor.intercept(record(LogLevel.DEBUG)));
        assertNull(interceptor.intercept(record(LogLevel.DEBUG)));

        now.set(61_000L);

        assertNotNull(interceptor.intercept(record(LogLevel.DEBUG)));
    }

    private static LogRecord record(LogLevel level) {
        return new LogRecord(
                0L, level, "t", "m", "thread", 1L, null, Collections.emptyMap());
    }
}
