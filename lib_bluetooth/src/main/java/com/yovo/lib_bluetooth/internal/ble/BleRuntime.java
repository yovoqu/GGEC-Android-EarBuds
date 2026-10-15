package com.yovo.lib_bluetooth.internal.ble;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoDispatcher;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoExecutors;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoCloseGuard;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * BLE 内部运行时：调度、回调与释放。
 */
public final class BleRuntime implements YoReleasable {

    private static final String TAG = "BleRuntime";

    private final YoCloseGuard closeGuard = new YoCloseGuard();
    private final ExecutorService ioExecutor = YoExecutors.cached("yo-ble-io");
    private final ScheduledExecutorService schedulerExecutor =
            YoExecutors.scheduled("yo-ble-scheduler");
    private final YoDispatcher callbackDispatcher = YoExecutors.mainDispatcher();
    private final YoTaskScheduler scheduler = YoExecutors.scheduler(schedulerExecutor);

    public void io(@NonNull Runnable task) {
        if (!closeGuard.isReleased()) {
            ioExecutor.execute(task);
        }
    }

    public void callback(@NonNull Runnable task) {
        if (!closeGuard.isReleased()) {
            callbackDispatcher.dispatch(task);
        }
    }

    @NonNull
    public YoTaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public void release() {
        if (!closeGuard.markReleased()) {
            YoBluetoothLogger.w(TAG, "BleRuntime 重复释放，已忽略");
            return;
        }
        YoBluetoothLogger.i(TAG, "正在释放 BleRuntime ...");
        ioExecutor.shutdownNow();
        schedulerExecutor.shutdownNow();
        YoBluetoothLogger.i(TAG, "BleRuntime 释放完成");
    }
}
