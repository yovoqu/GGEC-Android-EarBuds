package com.yovo.lib_common.log.internal.async;

import androidx.annotation.NonNull;

import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogRecord;
import com.yovo.lib_common.log.model.LogSink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 有界队列 + 单写线程：批量 drain + end-of-batch flush；溢出优先丢弃 V/D。
 */
public final class AsyncLogWriter {

    private static final long POLL_TIMEOUT_MS = 200L;
    private static final int BATCH_SIZE = 256;

    private final BlockingQueue<LogRecord> queue;
    private final List<LogSink> sinks;
    private final Set<LogLevel> urgentLevels;
    private final Thread writerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Object flushLock = new Object();
    private int flushRequests;

    public AsyncLogWriter(int capacity,
                          @NonNull Collection<LogSink> sinks,
                          @NonNull Set<LogLevel> urgentLevels) {
        this.queue = new LinkedBlockingQueue<>(Math.max(1, capacity));
        this.sinks = new ArrayList<>(sinks);
        this.urgentLevels = urgentLevels;
        writerThread = new Thread(this::loop, "YoLogWriter");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public void enqueue(@NonNull LogRecord record) {
        if (!running.get()) {
            return;
        }
        if (queue.offer(record)) {
            if (isUrgent(record)) {
                requestFlush();
            }
            return;
        }
        if (!dropFirst(candidate -> candidate.level() == LogLevel.VERBOSE
                || candidate.level() == LogLevel.DEBUG)) {
            if (!dropFirst(candidate -> candidate.level() != LogLevel.ERROR
                    && candidate.level() != LogLevel.ASSERT)) {
                if (isUrgent(record)) {
                    // 队列已满且都是紧急级别：丢最旧，保留当前紧急记录。
                    queue.poll();
                } else {
                    return;
                }
            }
        }
        if (queue.offer(record) && isUrgent(record)) {
            requestFlush();
        }
    }

    /** 通知写线程尽快刷盘（不等待）。 */
    public void requestFlush() {
        synchronized (flushLock) {
            flushRequests++;
            flushLock.notifyAll();
        }
    }

    /** 屏障等待写线程完成当前批次刷盘。 */
    public boolean flush(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (flushLock) {
            int request = flushRequests + 1;
            flushRequests = request;
            flushLock.notifyAll();
            long remaining;
            while ((remaining = deadline - System.currentTimeMillis()) > 0
                    && flushRequests >= request) {
                try {
                    flushLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return flushRequests < request;
                }
            }
            return flushRequests < request;
        }
    }

    public void shutdown() {
        running.set(false);
        synchronized (flushLock) {
            flushLock.notifyAll();
        }
        try {
            writerThread.join(15_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (LogSink sink : sinks) {
            sink.close();
        }
    }

    private boolean isUrgent(@NonNull LogRecord record) {
        return urgentLevels.contains(record.level());
    }

    private boolean dropFirst(@NonNull java.util.function.Predicate<LogRecord> predicate) {
        Iterator<LogRecord> iterator = queue.iterator();
        while (iterator.hasNext()) {
            LogRecord candidate = iterator.next();
            if (predicate.test(candidate)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private void loop() {
        while (running.get() || !queue.isEmpty()) {
            List<LogRecord> batch = new ArrayList<>(BATCH_SIZE);
            queue.drainTo(batch, BATCH_SIZE);
            if (batch.isEmpty()) {
                synchronized (flushLock) {
                    if (flushRequests > 0) {
                        flushRequests = 0;
                        flushLock.notifyAll();
                    }
                    try {
                        flushLock.wait(POLL_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        if (!running.get() && queue.isEmpty()) {
                            break;
                        }
                    }
                }
                continue;
            }
            for (LogRecord record : batch) {
                for (LogSink sink : sinks) {
                    sink.write(record);
                }
            }
            for (LogSink sink : sinks) {
                sink.flush();
            }
            synchronized (flushLock) {
                flushRequests = 0;
                flushLock.notifyAll();
            }
        }
    }
}
