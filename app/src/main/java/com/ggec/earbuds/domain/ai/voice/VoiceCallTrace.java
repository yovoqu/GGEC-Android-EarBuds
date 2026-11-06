package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 状态机转移轨迹：每条事件记一行 (序号, 时刻, 原状态, 事件, 新状态)。
 *
 * <p>为什么要有它：真机上"卡/乱"是时序问题，光看最终快照无法判断是谁先到。
 * 轨迹是环形缓冲（默认 256 条），可以整段导出来回放，也可以直接断言"没有在
 * SPEAKING 之后立刻回 LISTENING"。</p>
 */
public final class VoiceCallTrace {

    public record Entry(long seq, long atMillis, @NonNull VoiceCallState from,
                        @NonNull String event, @NonNull VoiceCallState to) {
    }

    private final int capacity;
    private final Deque<Entry> entries;
    private long nextSeq;

    public VoiceCallTrace() {
        this(256);
    }

    public VoiceCallTrace(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.entries = new ArrayDeque<>(this.capacity);
    }

    public synchronized void record(long atMillis, @NonNull VoiceCallState from,
                                     @NonNull VoiceCallEvent event, @NonNull VoiceCallState to) {
        if (entries.size() >= capacity) {
            entries.removeFirst();
        }
        entries.addLast(new Entry(++nextSeq, atMillis, from, event.getClass().getSimpleName(), to));
    }

    @NonNull
    public synchronized List<Entry> entries() {
        return List.copyOf(new ArrayList<>(entries));
    }

    public synchronized void clear() {
        entries.clear();
        nextSeq = 0;
    }
}
