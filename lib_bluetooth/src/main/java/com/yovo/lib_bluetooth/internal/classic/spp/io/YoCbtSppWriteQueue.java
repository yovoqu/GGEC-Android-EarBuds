package com.yovo.lib_bluetooth.internal.classic.spp.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 同步 SPP 写入队列，适用于无需专用写入线程的轻量场景。
 */
public final class YoCbtSppWriteQueue {

    private final OutputStream outputStream;
    private final int capacity;
    private final Queue<byte[]> queue;

    public YoCbtSppWriteQueue(@NonNull OutputStream outputStream, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got " + capacity);
        }
        this.outputStream = outputStream;
        this.capacity = capacity;
        this.queue = new LinkedList<>();
    }

    public YoCbtSppWriteQueue(@NonNull OutputStream outputStream) {
        this(outputStream, Integer.MAX_VALUE);
    }

    /**
     * 若在不超出容量的前提下可以立即入队，则将数据插入队列。
     *
     * @param data 待入队的字节数据
     * @return 若数据已加入则返回 true，队列已满则返回 false
     */
    public synchronized boolean offer(@NonNull byte[] data) {
        if (queue.size() >= capacity) {
            return false;
        }
        boolean added = queue.offer(data);
        if (added) {
            notifyAll();
        }
        return added;
    }

    /**
     * 最多等待 {@code timeoutMillis} 以获取队列中的数据。
     *
     * @return 下一条负载，若超时且队列为空则返回 {@code null}
     */
    @Nullable
    public synchronized byte[] poll(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (queue.isEmpty()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return null;
            }
            wait(remaining);
        }
        return queue.poll();
    }

    /**
     * 获取并移除队列头部元素，若队列为空则返回 null。
     */
    @Nullable
    public synchronized byte[] poll() {
        return queue.poll();
    }

    /**
     * 返回队列中当前元素数量。
     */
    public synchronized int size() {
        return queue.size();
    }

    /**
     * 原子地清空队列中的所有元素。
     */
    public synchronized void clear() {
        queue.clear();
    }

    /**
     * 将当前队列中的所有数据排空到指定 OutputStream。在开始写入前
     * 原子地清空队列，因此并发的 {@link #offer} 调用将作用于新队列。
     *
     * @param target 要写入的 OutputStream
     * @throws IOException 若写入失败
     */
    public void drainTo(@NonNull OutputStream target) throws IOException {
        Queue<byte[]> snapshot;
        synchronized (this) {
            snapshot = new LinkedList<>(queue);
            queue.clear();
        }
        for (byte[] data : snapshot) {
            target.write(data);
        }
        target.flush();
    }
}
