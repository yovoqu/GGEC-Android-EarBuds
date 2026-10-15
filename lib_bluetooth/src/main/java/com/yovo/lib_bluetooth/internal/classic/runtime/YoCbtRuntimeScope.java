package com.yovo.lib_bluetooth.internal.classic.runtime;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.lifecycle.YoCloseGuard;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 绑定单次操作的运行时作用域，释放后丢弃后续任务且不影响底层线程池。
 */
public final class YoCbtRuntimeScope implements YoReleasable {

    private static final String TAG = "YoCbtRuntimeScope";

    private final YoCbtRuntime runtime;
    private final YoCloseGuard scopeGuard = new YoCloseGuard();

    /**
     * 创建绑定到指定运行时的作用域。
     *
     * @param runtime 父级运行时，不可为 null
     */
    public YoCbtRuntimeScope(@NonNull YoCbtRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * 若本作用域或其父级运行时已释放，则返回 {@code true}。
     */
    public boolean isReleased() {
        return scopeGuard.isReleased() || runtime.isReleased();
    }

    /**
     * 将任务分派到 state 执行器，受作用域生命周期门控。
     */
    public void state(@NonNull Runnable task) {
        if (!isReleased()) {
            runtime.state(task);
        }
    }

    /**
     * 将任务分派到 IO 执行器，受作用域生命周期门控。
     */
    public void io(@NonNull Runnable task) {
        if (!isReleased()) {
            runtime.io(task);
        }
    }

    /**
     * 将任务分派到回调调度器，受作用域生命周期门控。
     */
    public void callback(@NonNull Runnable task) {
        if (!isReleased()) {
            runtime.callback(task);
        }
    }

    /**
     * 释放本作用域，此后通过本作用域提交的任务将被忽略。
     * <p>
     * 本方法是幂等的，重复调用会被忽略。
     */
    @Override
    public void release() {
        if (!scopeGuard.markReleased()) {
            return;
        }
        YoBluetoothLogger.d(TAG, "YoCbtRuntimeScope 已释放（运行时已释放="
                + runtime.isReleased() + "）");
    }
}
