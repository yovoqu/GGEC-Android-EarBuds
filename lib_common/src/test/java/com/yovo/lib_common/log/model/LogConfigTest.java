package com.yovo.lib_common.log.model;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class LogConfigTest {

    @Test
    public void invalidQueueCapacity_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                LogConfig.builder().queueCapacity(0).build());
    }

    @Test
    public void invalidRateLimit_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                LogConfig.builder().rateLimit(0, 60).build());
        assertThrows(IllegalArgumentException.class, () ->
                LogConfig.builder().rateLimit(10, 0).build());
    }

    @Test
    public void invalidCrashFlushTimeout_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                LogConfig.builder().crashFlushTimeoutMs(0).build());
    }
}
