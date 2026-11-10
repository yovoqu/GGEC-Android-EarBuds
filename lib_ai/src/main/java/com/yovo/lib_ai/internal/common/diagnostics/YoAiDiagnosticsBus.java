package com.yovo.lib_ai.internal.common.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * lib_ai 统一诊断总线（异常总线）：模块内“捕获 → 记录 → 上报”的唯一汇聚口。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #recordFailure(YoAiException)}：把 {@link YoAiException} 按 {@link YoAiErrors} 归类后
 *   记录错误日志、追加时间线、通知失败观察者；</li>
 *   <li>{@link #recordException(String, String, Throwable)}：未预期异常兜底；</li>
 *   <li>{@link #recordEvent(String, Object)}：诊断事件追加到时间线；</li>
 *   <li>{@link #timelineSnapshot()} / {@link #dumpReport()}：供诊断报告导出。</li>
 * </ul>
 *
 * <p>进程级单例 {@link #get()}，替代各处零散静默 catch / 裸日志，保证失败路径统一可观测。</p>
 */
public final class YoAiDiagnosticsBus {

    private static final String TAG = "YoAiDiagnostics";
    private static final int DEFAULT_CAPACITY = 256;
    private static final YoAiDiagnosticsBus INSTANCE = new YoAiDiagnosticsBus(DEFAULT_CAPACITY);

    @NonNull
    public static YoAiDiagnosticsBus get() {
        return INSTANCE;
    }

    /** 失败观察者：供上层遥测/上报使用（可选）。 */
    public interface FailureObserver {
        void onFailure(@NonNull YoAiErrorKind kind, @Nullable String message,
                       @NonNull YoAiException exception);
    }

    private final int capacity;
    private final ArrayDeque<String> events;
    private final CopyOnWriteArrayList<FailureObserver> observers = new CopyOnWriteArrayList<>();

    YoAiDiagnosticsBus(int capacity) {
        this.capacity = capacity > 0 ? capacity : DEFAULT_CAPACITY;
        this.events = new ArrayDeque<>(this.capacity);
    }

    public void addFailureObserver(@NonNull FailureObserver observer) {
        observers.addIfAbsent(observer);
    }

    public void removeFailureObserver(@NonNull FailureObserver observer) {
        observers.remove(observer);
    }

    /**
     * 记录结构化失败：按 {@link YoAiErrors} 归类 + 写错误日志 + 追加时间线 + 通知观察者。
     */
    public void recordFailure(@NonNull YoAiException exception) {
        YoAiErrorKind kind = YoAiErrors.kindOf(exception);
        String message = exception.getMessage();
        YoAiLogger.e(TAG, "[" + kind + "] " + (message == null ? "" : message), exception);
        append("failure:" + kind + ":" + (message == null ? "" : message));
        for (FailureObserver observer : observers) {
            observer.onFailure(kind, message, exception);
        }
    }

    /**
     * 记录未预期异常（兜底）。用于边界处捕获到无法转成 {@link YoAiException} 的异常。
     */
    public void recordException(@NonNull String tag, @NonNull String message,
                                @NonNull Throwable throwable) {
        YoAiLogger.e(tag, message, throwable);
        append("exception:" + tag + ":" + message);
    }

    /**
     * 记录诊断事件到时间线（不属于失败/异常）。
     */
    public void recordEvent(@NonNull String type, @Nullable Object payload) {
        append(type + ":" + payload);
    }

    @NonNull
    public synchronized List<String> timelineSnapshot() {
        return new ArrayList<>(events);
    }

    /**
     * 输出结构化诊断快照（失败/异常/事件时间线）。
     */
    @NonNull
    public synchronized String dumpReport() {
        StringBuilder builder = new StringBuilder("YoAiDiagnosticsBus{events=")
                .append(events.size())
                .append('}')
                .append('\n');
        for (String event : events) {
            builder.append("  ").append(event).append('\n');
        }
        return builder.toString();
    }

    /** 清空时间线（通常在一次会话结束时由调用方决定是否保留）。 */
    public synchronized void clear() {
        events.clear();
    }

    private synchronized void append(@NonNull String line) {
        if (events.size() >= capacity) {
            events.removeFirst();
        }
        events.addLast("[" + System.currentTimeMillis() + "] " + line);
    }
}
