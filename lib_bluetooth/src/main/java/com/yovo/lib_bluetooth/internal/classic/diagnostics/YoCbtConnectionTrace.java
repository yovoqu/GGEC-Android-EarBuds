package com.yovo.lib_bluetooth.internal.classic.diagnostics;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 按连接 ID 记录连接/断开/状态变更事件的诊断追踪器，线程安全。
 */
public final class YoCbtConnectionTrace {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<TraceEvent>> eventsByConnection;

    public YoCbtConnectionTrace() {
        this.eventsByConnection = new ConcurrentHashMap<>();
    }

    /**
     * 记录连接建立事件。
     *
     * @param connectionId 连接标识
     * @param deviceId     远端设备标识
     */
    public void recordConnect(@NonNull String connectionId, @NonNull String deviceId) {
        addEvent(connectionId, new TraceEvent(TraceEventType.CONNECT,
                "connected to device=" + deviceId));
    }

    /**
     * 记录断开连接事件。
     *
     * @param connectionId 连接标识
     * @param reason       断开原因的可读描述
     */
    public void recordDisconnect(@NonNull String connectionId, @NonNull String reason) {
        addEvent(connectionId, new TraceEvent(TraceEventType.DISCONNECT, reason));
    }

    /**
     * 记录状态变更事件。
     *
     * @param connectionId 连接标识
     * @param from         先前状态
     * @param to           新状态
     */
    public void recordStateChange(@NonNull String connectionId,
                                  @NonNull String from,
                                  @NonNull String to) {
        addEvent(connectionId, new TraceEvent(TraceEventType.STATE_CHANGE,
                "state: " + from + " -> " + to));
    }

    /**
     * 返回给定连接的所有追踪事件的不可修改快照。
     *
     * @param connectionId 连接标识
     * @return 追踪事件列表（未记录事件时可能为空）
     */
    @NonNull
    public List<TraceEvent> snapshot(@NonNull String connectionId) {
        CopyOnWriteArrayList<TraceEvent> events = eventsByConnection.get(connectionId);
        if (events == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * 清除指定连接的所有事件。
     *
     * @param connectionId 连接标识
     */
    public void clear(@NonNull String connectionId) {
        eventsByConnection.remove(connectionId);
    }

    /**
     * 清除所有连接的全部追踪事件。
     */
    public void clearAll() {
        eventsByConnection.clear();
    }

    // ── 内部 ─────────────────────────────────────────────────

    private void addEvent(@NonNull String connectionId, @NonNull TraceEvent event) {
        eventsByConnection
                .computeIfAbsent(connectionId, k -> new CopyOnWriteArrayList<>())
                .add(event);
    }

    // ── 类型 ────────────────────────────────────────────────────

    /**
     * 单条追踪事件，含时间戳、类型与描述。
     */
    public static final class TraceEvent {
        private final long timestamp;
        private final TraceEventType type;
        private final String description;

        TraceEvent(@NonNull TraceEventType type, @NonNull String description) {
            this.timestamp = System.currentTimeMillis();
            this.type = type;
            this.description = description;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @NonNull
        public TraceEventType getType() {
            return type;
        }

        @NonNull
        public String getDescription() {
            return description;
        }

        @NonNull
        @Override
        public String toString() {
            return "[" + timestamp + "] " + type + ": " + description;
        }
    }

    /**
     * 追踪事件类型枚举。
     */
    public enum TraceEventType {
        CONNECT,
        DISCONNECT,
        STATE_CHANGE
    }
}
