package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.List;

/**
 * 软件 lost 清扫调度：按固定间隔在 state 线程上轮询聚合设备，把超过末次广播阈值的设备交给控制器
 * 以 {@code onScanResultLost} 上报。对齐 Nordic 语义——只在仍有在册设备时继续轮询，空集即停表，
 * 避免 1s 空转唤醒。
 *
 * <p>本类只负责定时与代际判定（哪一代扫描还活着、是否该继续下一轮），清扫与派发由控制器执行。</p>
 */
final class YoBleScanLostSweeper {

    private static final long SWEEP_INTERVAL_MS = 1_000L;

    interface Host {
        /** 该代扫描是否仍在进行（未停止、未释放、配置未清空）。 */
        boolean isSessionAlive(long generation);

        /** 当前配置是否启用了软件 lost 判定。 */
        boolean isLostTrackingEnabled();

        int activeDeviceCount();

        /** 清扫超过末次广播阈值的设备。 */
        @NonNull
        List<BleScanResult> sweepExpiredDevices();

        /** 把本轮的 lost 结果派发给上层。 */
        void dispatchLostResults(@NonNull List<BleScanResult> lost);
    }

    private final YoBleRuntime runtime;
    private final Host host;
    @Nullable
    private YoTimeoutHandle handle;

    YoBleScanLostSweeper(@NonNull YoBleRuntime runtime, @NonNull Host host) {
        this.runtime = runtime;
        this.host = host;
    }

    /**
     * 按需起表：已有定时器、会话已结束、未启用软件 lost 或当前无在册设备时都不启动，
     * 保证空闲扫描不产生周期唤醒。
     */
    void arm(long generation) {
        if (handle != null || !host.isSessionAlive(generation)) {
            return;
        }
        if (host.isLostTrackingEnabled() && host.activeDeviceCount() > 0) {
            schedule(generation);
        }
    }

    void disarm() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    private void schedule(long generation) {
        if (handle != null || !host.isSessionAlive(generation)) {
            return;
        }
        handle = runtime.scheduler().schedule(() -> runtime.state(() -> {
            handle = null;
            if (!host.isSessionAlive(generation)) {
                return;
            }
            host.dispatchLostResults(host.sweepExpiredDevices());
            if (host.activeDeviceCount() > 0) {
                schedule(generation);
            }
        }), SWEEP_INTERVAL_MS);
    }
}
