package com.yovo.lib_ai.usage;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 内存账本：明细只留最近若干条，累计从不清零。
 *
 * <p>为什么明细要裁剪、累计不裁剪：调用方真正关心的是"一共花了多少"，这个数必须完整；
 * 明细只是用来看"最近发生了什么"，无上限会一直涨内存。所以超出容量时丢最旧的一条，
 * 但它已经算进累计里了——{@link #summary()} 的总数不会因为裁剪而变小。
 *
 * <p>进程结束即丢失。要跨进程保留，自己实现 {@link YoUsageLedger} 把记录落到磁盘，
 * 口径用 {@link YoUsageSummary#of} 对齐即可。
 */
public final class YoMemoryUsageLedger implements YoUsageLedger {

    /** 默认保留的明细条数。 */
    public static final int DEFAULT_CAPACITY = 500;

    private final int capacity;
    private final Deque<YoUsageRecord> records = new ArrayDeque<>();
    private final YoUsageAccumulator lifetime = new YoUsageAccumulator();

    public YoMemoryUsageLedger() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * @param capacity 明细最多保留多少条，必须为正
     */
    public YoMemoryUsageLedger(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized void record(@NonNull YoUsageRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record is required");
        }
        lifetime.add(record);
        records.addLast(record);
        while (records.size() > capacity) {
            records.removeFirst();
        }
    }

    @NonNull
    @Override
    public synchronized List<YoUsageRecord> records() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    @NonNull
    @Override
    public synchronized YoUsageSummary summary() {
        return lifetime.summary();
    }

    @Override
    public synchronized int size() {
        return records.size();
    }

    @Override
    public synchronized void clear() {
        records.clear();
        lifetime.clear();
    }

    /** 明细窗口的容量。 */
    public int capacity() {
        return capacity;
    }
}
