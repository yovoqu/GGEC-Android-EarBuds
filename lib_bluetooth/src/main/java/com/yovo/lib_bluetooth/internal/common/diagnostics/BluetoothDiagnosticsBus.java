package com.yovo.lib_bluetooth.internal.common.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoFailure;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 蓝牙模块统一诊断总线（异常总线）：模块内“捕获 → 记录 → 上报”的唯一汇聚口。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #recordFailure(YoFailure)}：结构化失败（{@code YoFailure}）统一记录——写错误日志、
 *   追加事件时间线、按 traceId 幂等去重、通知失败观察者；</li>
 *   <li>{@link #recordException(String, Throwable)}：未预期异常兜底——写错误日志并进入时间线；</li>
 *   <li>{@link #recordEvent(String, Object)}：诊断事件追加到时间线；</li>
 *   <li>{@link #timelineSnapshot()} / {@link #dumpReport()}：供诊断报告导出。</li>
 * </ul>
 *
 * <p>进程级单例 {@link #get()}，替代各处零散的静默 catch / 直接裸日志，保证 BLE 与 Classic 对称。</p>
 */
public final class BluetoothDiagnosticsBus {

    private static final String TAG = "BluetoothDiagnostics";
    private static final int DEFAULT_CAPACITY = 256;
    private static final BluetoothDiagnosticsBus INSTANCE = new BluetoothDiagnosticsBus(DEFAULT_CAPACITY);

    @NonNull
    public static BluetoothDiagnosticsBus get() {
        return INSTANCE;
    }

    /** 失败观察者：供上层遥测/上报使用（可选）。 */
    public interface FailureObserver {
        void onFailure(@NonNull YoFailure failure);
    }

    private final YoEventTimeline timeline;
    private final Set<String> recordedTraceIds = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<FailureObserver> observers = new CopyOnWriteArrayList<>();

    public BluetoothDiagnosticsBus(int capacity) {
        this.timeline = new YoEventTimeline(capacity);
    }

    public void addFailureObserver(@NonNull FailureObserver observer) {
        observers.addIfAbsent(observer);
    }

    public void removeFailureObserver(@NonNull FailureObserver observer) {
        observers.remove(observer);
    }

    /**
     * 记录结构化失败：写错误日志 + 追加时间线 + 幂等去重 + 通知观察者。
     */
    public void recordFailure(@NonNull YoFailure failure) {
        final String traceId = failure.getTraceId();
        if (traceId != null && !recordedTraceIds.add(traceId)) {
            return;
        }
        final String code = failure.getErrorCode().code();
        YoBtLogger.e(TAG, "[" + code + "] " + failure.getMessage()
                + " (source=" + failure.getSourceModule()
                + ", trace=" + traceId + ")", failure.getCause());
        timeline.record("failure:" + code, failure.getMessage());
        for (FailureObserver observer : observers) {
            observer.onFailure(failure);
        }
    }

    /**
     * 记录未预期异常（兜底）。用于边界处捕获到无法转成 {@link YoFailure} 的异常。
     */
    public void recordException(@NonNull String tag, @NonNull Throwable throwable) {
        recordException(tag, "uncaught exception routed to diagnostics bus", throwable);
    }

    /**
     * 记录未预期异常（带上下文消息）。
     */
    public void recordException(@NonNull String tag, @NonNull String message,
                                @NonNull Throwable throwable) {
        YoBtLogger.e(tag, message, throwable);
        timeline.record("exception:" + tag, message);
    }

    /**
     * 记录诊断事件到时间线（不属于失败/异常）。
     */
    public void recordEvent(@NonNull String type, @Nullable Object payload) {
        timeline.record(type, String.valueOf(payload));
    }

    @NonNull
    public List<YoEventTimeline.TimedEvent> timelineSnapshot() {
        return timeline.snapshot();
    }

    /**
     * 输出结构化诊断快照（失败/异常/事件时间线）。供 {@code exportDiagnosticReport()} 汇总。
     */
    @NonNull
    public String dumpReport() {
        StringBuilder sb = new StringBuilder("BluetoothDiagnosticsBus{events=")
                .append(timeline.size())
                .append(',')
                .append('\n');
        for (YoEventTimeline.TimedEvent event : timeline.snapshot()) {
            sb.append("  ").append(event.toString()).append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    /** 清空时间线与去重集合（通常在一次会话结束时由调用方决定是否保留）。 */
    public void clear() {
        timeline.clear();
        recordedTraceIds.clear();
    }
}
