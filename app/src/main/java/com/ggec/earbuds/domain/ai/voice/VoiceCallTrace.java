package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * 语音通话 actor 的现场排障记录，两条互补的流：
 *
 * <ul>
 *   <li>{@link #entries()}：转移轨迹 (序号, 时刻, 原状态, 事件, 新状态)；
 *       真机上"卡/乱"是时序问题，光看最终快照无法判断是谁先到，轨迹可以整段导出回放；</li>
 *   <li>{@link #failures()}：分类失败 (序号, 时刻, 上报点, 失败)。
 *       清理期与调度期的异常无法再进状态机（终态已定或执行器已关停），
 *       它们必须在这里留下分类，不能只在 catch 块里蒸发。</li>
 * </ul>
 *
 * <p>两条流都是环形缓冲（默认 256 条）。</p>
 */
public final class VoiceCallTrace {

    public record Entry(long seq, long atMillis, @NonNull VoiceCallState from,
                        @NonNull String event, @NonNull VoiceCallState to) {
    }

    /** 上报点（{@code site}）是数据层里的具体动作，例如 {@code StartUplink}、{@code link.Cancel}。 */
    public record Failure(long seq, long atMillis, @NonNull String site,
                          @NonNull AiFailure failure) {
    }

    private final int capacity;
    private final Deque<Entry> entries;
    private final Deque<Failure> failures;
    private long nextSeq;
    private long nextFailureSeq;

    public VoiceCallTrace() {
        this(256);
    }

    public VoiceCallTrace(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.entries = new ArrayDeque<>(this.capacity);
        this.failures = new ArrayDeque<>(this.capacity);
    }

    public synchronized void record(long atMillis, @NonNull VoiceCallState from,
                                    @NonNull VoiceCallEvent event, @NonNull VoiceCallState to) {
        if (entries.size() >= capacity) {
            entries.removeFirst();
        }
        entries.addLast(new Entry(++nextSeq, atMillis, from, event.getClass().getSimpleName(), to));
    }

    /** 上报一次分类失败；调用方已经决定了失败分类，这里只负责留痕。 */
    public synchronized void recordFailure(long atMillis, @NonNull String site,
                                           @NonNull AiFailure failure) {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(failure, "failure");
        if (failures.size() >= capacity) {
            failures.removeFirst();
        }
        failures.addLast(new Failure(++nextFailureSeq, atMillis, site, failure));
    }

    @NonNull
    public synchronized List<Entry> entries() {
        return List.copyOf(new ArrayList<>(entries));
    }

    @NonNull
    public synchronized List<Failure> failures() {
        return List.copyOf(new ArrayList<>(failures));
    }

    public synchronized void clear() {
        entries.clear();
        failures.clear();
        nextSeq = 0;
        nextFailureSeq = 0;
    }
}
