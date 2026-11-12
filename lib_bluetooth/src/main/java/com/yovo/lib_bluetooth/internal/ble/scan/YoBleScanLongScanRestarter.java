package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;

/**
 * 长扫重启调度：系统会在扫描超过 30 分钟后降级为 opportunistic，因此在
 * {@link YoBleScanLongScanPolicy#RESTART_INTERVAL_MS} 前主动重启一次底层扫描。
 *
 * <p>本类只负责定时与代际跟随，重启动作由控制器执行（它才持有当前会话与平台端口）。</p>
 */
final class YoBleScanLongScanRestarter {

    interface Host {
        /** 重启该代扫描的平台侧扫描（控制器内部会再校验该代是否仍然存活）。 */
        void restartPlatformScan(long generation);
    }

    private final YoBleRuntime runtime;
    private final Host host;
    @Nullable
    private YoTimeoutHandle handle;

    YoBleScanLongScanRestarter(@NonNull YoBleRuntime runtime, @NonNull Host host) {
        this.runtime = runtime;
        this.host = host;
    }

    /** 每次底层扫描启动成功后重新计时；显式短超时的会话无需重启。 */
    void arm(long generation, long timeoutMillis) {
        disarm();
        if (!YoBleScanLongScanPolicy.shouldArm(timeoutMillis)) {
            return;
        }
        handle = runtime.stateDelayed(() -> {
            handle = null;
            host.restartPlatformScan(generation);
        }, YoBleScanLongScanPolicy.RESTART_INTERVAL_MS);
    }

    void disarm() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }
}
