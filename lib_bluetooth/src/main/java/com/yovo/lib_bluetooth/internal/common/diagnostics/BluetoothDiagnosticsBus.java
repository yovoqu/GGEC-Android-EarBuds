package com.yovo.lib_bluetooth.internal.common.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoFailure;
import com.yovo.lib_bluetooth.spi.log.LogSink;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p><b>装配归属：</b>本类没有进程级单例，也没有静态工厂。实例由客户端装配点
 * （{@code BleClientImpl} / {@code ClassicClientImpl}）构造后沿构造参数注入运行时
 * （{@link com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime#diagnostics()} /
 * {@link com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime#diagnostics()}），
 * 各组件只读这一个实例。</p>
 *
 * <p><b>去重集合上限：</b>{@code recordedTraceIds} 是有界窗口（默认
 * {@value #DEFAULT_RECORDED_TRACE_LIMIT} 条），最旧的 traceId 先被淘汰；它只做“同一 traceId
 * 不重复上报”的短期去重，不是全量历史。客户端释放时 {@link #clear()} 释放全部诊断状态。</p>
 */
public final class BluetoothDiagnosticsBus {

    private static final String TAG = "BluetoothDiagnostics";

    /** 事件时间线默认容量。 */
    public static final int DEFAULT_CAPACITY = 256;

    /** traceId 去重窗口默认上限：足够覆盖一次会话的在飞失败，又不会无界增长。 */
    public static final int DEFAULT_RECORDED_TRACE_LIMIT = 512;

    /** 失败观察者：供上层遥测/上报使用（可选）。 */
    public interface FailureObserver {
        void onFailure(@NonNull YoFailure failure);
    }

    private final YoEventTimeline timeline;
    private final LogSink logSink;
    private final CopyOnWriteArrayList<FailureObserver> observers = new CopyOnWriteArrayList<>();

    /** traceId 去重窗口：按插入顺序淘汰最旧项，容量由构造参数固定。 */
    private final Object traceLock = new Object();
    private final Map<String, Boolean> recordedTraceIds;
    private final int recordedTraceLimit;

    public BluetoothDiagnosticsBus(@NonNull LogSink logSink, int capacity) {
        this(logSink, capacity, DEFAULT_RECORDED_TRACE_LIMIT);
    }

    public BluetoothDiagnosticsBus(@NonNull LogSink logSink, int capacity, int recordedTraceLimit) {
        this.logSink = logSink;
        this.timeline = new YoEventTimeline(capacity);
        this.recordedTraceLimit = Math.max(1, recordedTraceLimit);
        this.recordedTraceIds = new LinkedHashMap<>();
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
        if (traceId != null && !markRecorded(traceId)) {
            return;
        }
        final String code = failure.getErrorCode().code();
        logSink.e(TAG, "[" + code + "] " + failure.getMessage()
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
        logSink.e(tag, message, throwable);
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

    /** 清空时间线、去重窗口与失败观察者：客户端释放路径调用，诊断状态不跨会话留存。 */
    public void clear() {
        timeline.clear();
        synchronized (traceLock) {
            recordedTraceIds.clear();
        }
        observers.clear();
    }

    /** 首次见到该 traceId 返回 {@code true}；窗口满时淘汰最旧一条。 */
    private boolean markRecorded(@NonNull String traceId) {
        synchronized (traceLock) {
            if (recordedTraceIds.containsKey(traceId)) {
                return false;
            }
            recordedTraceIds.put(traceId, Boolean.TRUE);
            Iterator<String> oldest = recordedTraceIds.keySet().iterator();
            while (recordedTraceIds.size() > recordedTraceLimit && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
            return true;
        }
    }
}
