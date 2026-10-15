package com.yovo.lib_common.log.internal.async;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogRecord;
import com.yovo.lib_common.log.model.LogSink;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AsyncLogWriterTest {

    @Test
    public void overflow_dropsVerboseAndDebugKeepsUrgent() throws Exception {
        BlockingSink sink = new BlockingSink();
        AsyncLogWriter writer = new AsyncLogWriter(
                1, Collections.singletonList(sink), EnumSet.of(LogLevel.ERROR, LogLevel.ASSERT));

        writer.enqueue(record("first", LogLevel.INFO));
        assertTrue(sink.firstWrite.await(2, TimeUnit.SECONDS));

        writer.enqueue(record("v", LogLevel.VERBOSE));
        writer.enqueue(record("d", LogLevel.DEBUG));
        writer.enqueue(record("e", LogLevel.ERROR));

        sink.blocking = false;
        sink.release.countDown();
        assertTrue(writer.flush(2000));
        writer.shutdown();

        assertEquals(Arrays.asList("first", "e"), sink.names());
    }

    @Test
    public void flush_barrierWaitsForBatch() {
        RecordingSink sink = new RecordingSink();
        AsyncLogWriter writer = new AsyncLogWriter(
                8, Collections.singletonList(sink), EnumSet.of(LogLevel.ERROR));

        writer.enqueue(record("a", LogLevel.INFO));
        writer.enqueue(record("b", LogLevel.ERROR));
        boolean flushed = writer.flush(2000);

        assertTrue(flushed);
        assertEquals(Arrays.asList("a", "b"), sink.names());
        assertTrue(sink.flushCount.get() >= 1);
        writer.shutdown();
    }

    @Test
    public void shutdown_drainsPendingRecords() {
        RecordingSink sink = new RecordingSink();
        AsyncLogWriter writer = new AsyncLogWriter(
                8, Collections.singletonList(sink), EnumSet.noneOf(LogLevel.class));

        writer.enqueue(record("a", LogLevel.INFO));
        writer.shutdown();

        assertEquals(Collections.singletonList("a"), sink.names());
    }

    @Test
    public void flush_emptyQueueReturnsPromptly() {
        RecordingSink sink = new RecordingSink();
        AsyncLogWriter writer = new AsyncLogWriter(
                8, Collections.singletonList(sink), EnumSet.noneOf(LogLevel.class));

        boolean flushed = writer.flush(1000);

        assertTrue(flushed);
        assertTrue(sink.records.isEmpty());
        writer.shutdown();
    }

    private static LogRecord record(String message, LogLevel level) {
        return new LogRecord(
                System.currentTimeMillis(),
                level,
                "t",
                message,
                "test",
                1L,
                null,
                Collections.emptyMap());
    }

    private static final class BlockingSink implements LogSink {

        final CountDownLatch firstWrite = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final List<LogRecord> records =
                Collections.synchronizedList(new ArrayList<>());
        volatile boolean blocking = true;

        @Override
        public void write(LogRecord record) {
            if (blocking && records.isEmpty()) {
                firstWrite.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<String> names() {
            List<String> names = new ArrayList<>();
            synchronized (records) {
                for (LogRecord record : records) {
                    names.add(record.message());
                }
            }
            return names;
        }
    }

    private static final class RecordingSink implements LogSink {

        final List<LogRecord> records =
                Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger flushCount = new AtomicInteger();

        @Override
        public void write(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            flushCount.incrementAndGet();
        }

        @Override
        public void close() {
        }

        List<String> names() {
            List<String> names = new ArrayList<>();
            synchronized (records) {
                for (LogRecord record : records) {
                    names.add(record.message());
                }
            }
            return names;
        }
    }
}
