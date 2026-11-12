package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;

/**
 * 软件批处理 flush 调度：平台不支持硬件批量时，按 {@code reportDelay} 周期性把缓冲结果成批投递，
 * 并对齐「同一代扫描只保留一条 flush 循环」——重新启动前由控制器先 {@link #stop()}。
 *
 * <p>本类只负责定时与代际判定；缓冲与投递由控制器执行。</p>
 */
final class YoBleScanBatchFlusher {

    interface Host {
        /** 该代扫描是否仍在进行（未停止、未释放、配置未清空）。 */
        boolean isSessionAlive(long generation);

        /** 是否处于软件批处理模式。 */
        boolean isSoftwareBatchActive();

        /** 两次 flush 的间隔（毫秒）。 */
        long flushIntervalMillis();

        /** 立即把已缓冲结果投递给上层。 */
        void flushBufferedResults();
    }

    private final YoBleRuntime runtime;
    private final Host host;
    @Nullable
    private YoTimeoutHandle handle;

    YoBleScanBatchFlusher(@NonNull YoBleRuntime runtime, @NonNull Host host) {
        this.runtime = runtime;
        this.host = host;
    }

    /** 起一条 flush 循环；未处于软件批处理模式或会话已结束时不起。 */
    void start(long generation) {
        if (!host.isSoftwareBatchActive() || !host.isSessionAlive(generation)) {
            return;
        }
        handle = runtime.stateDelayed(() -> {
            if (!host.isSoftwareBatchActive() || !host.isSessionAlive(generation)) {
                return;
            }
            host.flushBufferedResults();
            start(generation);
        }, host.flushIntervalMillis());
    }

    void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }
}
