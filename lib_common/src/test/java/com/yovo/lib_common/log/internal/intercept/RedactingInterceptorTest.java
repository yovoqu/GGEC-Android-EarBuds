package com.yovo.lib_common.log.internal.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogRecord;

import org.junit.Test;

import java.util.Collections;

public final class RedactingInterceptorTest {

    private final RedactingInterceptor interceptor = new RedactingInterceptor();

    @Test
    public void redactsEmailBearerAndLongDigits() {
        LogRecord record = interceptor.intercept(record(
                "contact user@example.com, token Bearer abc123XYZ, card 1234567890123456"));

        assertFalse(record.message().contains("user@example.com"));
        assertFalse(record.message().contains("abc123XYZ"));
        assertFalse(record.message().contains("1234567890123456"));
        assertEquals(3, count("[REDACTED]", record.message()));
    }

    @Test
    public void plainMessageUnchanged() {
        LogRecord record = interceptor.intercept(record("hello world"));

        assertEquals("hello world", record.message());
    }

    private static int count(String needle, String text) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static LogRecord record(String message) {
        return new LogRecord(
                0L, LogLevel.INFO, "t", message, "thread", 1L, null, Collections.emptyMap());
    }
}
