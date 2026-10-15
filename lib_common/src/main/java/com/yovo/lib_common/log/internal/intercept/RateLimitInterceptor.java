package com.yovo.lib_common.log.internal.intercept;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.model.LogInterceptor;
import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogRecord;

import java.util.function.LongSupplier;

/**
 * 窗口内条数限流；超限丢弃，ERROR/ASSERT 保留。
 */
public final class RateLimitInterceptor implements LogInterceptor {

    private final int maxPerWindow;

    private final long windowMillis;

    private final Object lock = new Object();

    private final LongSupplier nowMillis;

    private long windowStart;

    private int count;

    public RateLimitInterceptor(int maxPerWindow, int windowSeconds) {
        this(maxPerWindow, windowSeconds, System::currentTimeMillis);
    }

    RateLimitInterceptor(int maxPerWindow,
                         int windowSeconds,
                         @NonNull LongSupplier nowMillis) {
        if (maxPerWindow <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException(
                    "rate limit values must be positive");
        }
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = windowSeconds * 1000L;
        this.nowMillis = nowMillis;
        windowStart = nowMillis.getAsLong();
    }

    @Override
    @Nullable
    public LogRecord intercept(@NonNull LogRecord record) {
        synchronized (lock) {
            long now = nowMillis.getAsLong();
            if (now - windowStart >= windowMillis) {
                windowStart = now;
                count = 0;
            }
            if (count >= maxPerWindow) {
                return record.level() == LogLevel.ERROR
                        || record.level() == LogLevel.ASSERT
                        ? record
                        : null;
            }
            count++;
            return record;
        }
    }
}
