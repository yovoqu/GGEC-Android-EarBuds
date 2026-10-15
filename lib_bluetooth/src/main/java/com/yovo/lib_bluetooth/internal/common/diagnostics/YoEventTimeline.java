package com.yovo.lib_bluetooth.internal.common.diagnostics;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 带容量上限的事件时间线，按序记录诊断事件。
 */
public final class YoEventTimeline {
    private final List<TimedEvent> events;
    private final int maxCapacity;

    public YoEventTimeline() {
        this(256);
    }

    public YoEventTimeline(int maxCapacity) {
        this.events = new ArrayList<>();
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : 256;
    }

    /**
     * 记录带 tag 和 message 的定时事件，时间戳取自 {@link System#currentTimeMillis()}。
     */
    public synchronized void record(@NonNull String tag, @NonNull String message) {
        if (events.size() >= maxCapacity) {
            events.remove(0);
        }
        events.add(new TimedEvent(tag, message, System.currentTimeMillis()));
    }

    /**
     * 返回所有已记录事件的不可修改快照。
     */
    @NonNull
    public synchronized List<TimedEvent> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * 清除所有已记录事件。
     */
    public synchronized void clear() {
        events.clear();
    }

    /**
     * 返回已记录事件数量。
     */
    public synchronized int size() {
        return events.size();
    }

    // ── TimedEvent 内部类 ─────────────────────────────────────────

    public static final class TimedEvent {
        private final String tag;
        private final String message;
        private final long timestampMillis;

        private TimedEvent(@NonNull String tag,
                           @NonNull String message,
                           long timestampMillis) {
            this.tag = tag;
            this.message = message;
            this.timestampMillis = timestampMillis;
        }

        @NonNull
        public String tag() {
            return tag;
        }

        @NonNull
        public String message() {
            return message;
        }

        public long timestampMillis() {
            return timestampMillis;
        }

        @NonNull
        @Override
        public String toString() {
            return "[" + timestampMillis + "] " + tag + ": " + message;
        }
    }
}
