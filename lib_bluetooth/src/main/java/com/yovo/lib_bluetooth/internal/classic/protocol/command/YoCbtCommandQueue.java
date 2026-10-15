package com.yovo.lib_bluetooth.internal.classic.protocol.command;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单 SPP 连接的有界待处理命令队列，按 ID 存储并等待响应匹配。
 */
public final class YoCbtCommandQueue {

    private final int capacity;
    private final LinkedHashMap<String, PendingEntry> pending;

    public YoCbtCommandQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.pending = new LinkedHashMap<>();
    }

    public YoCbtCommandQueue() {
        this(64);
    }

    /**
     * 将命令请求入队。若队列已满则返回 false。
     */
    public synchronized boolean enqueue(@NonNull YoCbtCommandRequest request,
                                         @NonNull Runnable timeoutCallback) {
        if (pending.size() >= capacity) return false;
        pending.put(request.getCommandId(), new PendingEntry(request, timeoutCallback));
        return true;
    }

    /**
     * 查找并移除与给定数据包匹配的第一个待处理命令。
     */
    @Nullable
    public synchronized YoCbtCommandRequest matchAndRemove(@NonNull YoCbtPacket packet) {
        Iterator<Map.Entry<String, PendingEntry>> iter = pending.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, PendingEntry> entry = iter.next();
            PendingEntry pendingEntry = entry.getValue();
            if (pendingEntry.request.getResponseMatcher().matches(pendingEntry.request, packet)) {
                iter.remove();
                return pendingEntry.request;
            }
        }
        return null;
    }

    /**
     * 按 ID 移除指定命令。返回被移除的请求，若无则返回 null。
     */
    @Nullable
    public synchronized YoCbtCommandRequest remove(@NonNull String commandId) {
        PendingEntry entry = pending.remove(commandId);
        return entry != null ? entry.request : null;
    }

    /**
     * 对最旧的待处理命令调用超时回调（FIFO 超时）。
     */
    @Nullable
    public synchronized Runnable pollTimeout() {
        Iterator<Map.Entry<String, PendingEntry>> iter = pending.entrySet().iterator();
        if (iter.hasNext()) {
            Map.Entry<String, PendingEntry> entry = iter.next();
            iter.remove();
            return entry.getValue().timeoutCallback;
        }
        return null;
    }

    public synchronized int size() {
        return pending.size();
    }

    public synchronized boolean isEmpty() {
        return pending.isEmpty();
    }

    public synchronized boolean isFull() {
        return pending.size() >= capacity;
    }

    public synchronized void clear() {
        pending.clear();
    }

    private static final class PendingEntry {
        final YoCbtCommandRequest request;
        final Runnable timeoutCallback;

        PendingEntry(YoCbtCommandRequest request, Runnable timeoutCallback) {
            this.request = request;
            this.timeoutCallback = timeoutCallback;
        }
    }
}
