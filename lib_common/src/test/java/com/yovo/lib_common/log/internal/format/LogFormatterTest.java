package com.yovo.lib_common.log.internal.format;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogRecord;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LogFormatterTest {

    @Test
    public void format_includesTimestampLevelTagThreadAndContext() {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("jobId", "42");
        LogRecord record = new LogRecord(
                1_720_000_000_123L,
                LogLevel.ERROR,
                "Tag",
                "boom",
                "main",
                1L,
                null,
                context);

        String line = new LogFormatter().format(record, true, true);

        assertTrue(line.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} ERROR/Tag.*"));
        assertTrue(line.contains("ERROR/Tag"));
        assertTrue(line.contains("[main]"));
        assertTrue(line.contains(": boom"));
        assertTrue(line.contains("{jobId=42}"));
    }

    @Test
    public void format_skipsThreadAndContextWhenDisabled() {
        LogRecord record = new LogRecord(
                0L, LogLevel.INFO, "T", "msg", "thread", 1L, null, Collections.emptyMap());

        String line = new LogFormatter().format(record, false, false);

        assertTrue(line.endsWith(": msg"));
        assertTrue(!line.contains("[thread]"));
    }

    @Test
    public void throwableText_rendersStackTrace() {
        RuntimeException error = new IllegalStateException("boom-detail");

        String text = new LogFormatter().throwableText(error);

        assertTrue(text.contains("IllegalStateException"));
        assertTrue(text.contains("boom-detail"));
        assertTrue(text.contains("LogFormatterTest"));
    }

    @Test
    public void throwableText_nullReturnsEmpty() {
        assertEquals("", new LogFormatter().throwableText(null));
    }
}
