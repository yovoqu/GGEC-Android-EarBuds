package com.yovo.lib_bluetooth.internal.ble.runtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoDispatcher;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoExecutors;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoCloseGuard;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE 内部核心运行时：统一管理单线程 state 串行执行器、IO 线程池、定时器、回调分发器与优雅释放顺序。
 */
public final class YoBleRuntime implements YoReleasable {

    private static final String TAG = "YoBleRuntime";

    private final long shutdownTimeoutSeconds;
    private final String stateThreadName;
    private final boolean usingCustomStateDispatcher;
    private final YoCloseGuard closeGuard = new YoCloseGuard();
    @Nullable
    private final ExecutorService stateExecutor;
    @NonNull
    private final YoDispatcher stateDispatcher;
    @NonNull
    private final ExecutorService ioExecutor;
    @Nullable
    private final ExecutorService callbackExecutor;
    @NonNull
    private final YoDispatcher callbackDispatcher;
    @Nullable
    private final ScheduledExecutorService schedulerExecutor;
    @NonNull
    private final YoTaskScheduler scheduler;
    private final YoBleEventHub eventHub = new YoBleEventHub();
    private final AtomicReference<Thread> stateThreadRef = new AtomicReference<>(null);

    public YoBleRuntime() {
        this(YoBleRuntimeConfig.builder().build());
    }

    public YoBleRuntime(@NonNull YoBleRuntimeConfig config) {
        this.stateThreadName = config.stateThreadName();
        this.shutdownTimeoutSeconds = config.shutdownTimeoutSeconds();

        if (config.customStateDispatcher() != null) {
            this.usingCustomStateDispatcher = true;
            this.stateExecutor = null;
            this.stateDispatcher = config.customStateDispatcher();
        } else {
            this.usingCustomStateDispatcher = false;
            this.stateExecutor = YoExecutors.single(stateThreadName);
            this.stateDispatcher = stateExecutor::execute;
            stateExecutor.execute(() -> stateThreadRef.set(Thread.currentThread()));
        }

        this.ioExecutor = YoExecutors.fixed("yo-ble-io", config.ioThreadPoolSize());

        if (config.customScheduler() != null) {
            this.schedulerExecutor = null;
            this.scheduler = config.customScheduler();
        } else {
            this.schedulerExecutor = YoExecutors.scheduled("yo-ble-scheduler");
            this.scheduler = YoExecutors.scheduler(schedulerExecutor);
        }

        if (config.customCallbackDispatcher() != null) {
            this.callbackExecutor = null;
            this.callbackDispatcher = config.customCallbackDispatcher();
        } else if (config.callbackOnMainThread()) {
            this.callbackExecutor = null;
            this.callbackDispatcher = YoExecutors.mainDispatcher();
        } else {
            this.callbackExecutor = YoExecutors.single("yo-ble-callback");
            this.callbackDispatcher = callbackExecutor::execute;
        }
    }

    public void state(@NonNull Runnable task) {
        if (!isReleased()) {
            try {
                stateDispatcher.dispatch(task);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }

    public void io(@NonNull Runnable task) {
        if (!isReleased()) {
            try {
                ioExecutor.execute(task);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }

    public void callback(@NonNull Runnable task) {
        if (!isReleased()) {
            try {
                callbackDispatcher.dispatch(task);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }

    @NonNull
    public YoTaskScheduler scheduler() {
        return scheduler;
    }

    @NonNull
    public YoBleEventHub eventHub() {
        return eventHub;
    }

    public void checkStateThread() {
        if (usingCustomStateDispatcher) {
            return;
        }
        Thread current = Thread.currentThread();
        Thread expected = stateThreadRef.get();
        if (expected == null) {
            if (!current.getName().startsWith(stateThreadName)) {
                throw new IllegalStateException(
                        "Must be called from BLE state thread, called from: " + current.getName());
            }
        } else if (current != expected) {
            throw new IllegalStateException(
                    "Must be called from BLE state thread (" + expected.getName()
                            + "), called from: " + current.getName());
        }
    }

    public void checkIoThread() {
        Thread current = Thread.currentThread();
        if (!current.getName().startsWith("yo-ble-io")) {
            throw new IllegalStateException(
                    "Must be called from BLE IO thread, called from: " + current.getName());
        }
    }

    @NonNull
    public YoBleRuntimeScope createScope() {
        return new YoBleRuntimeScope(this);
    }

    public boolean isReleased() {
        return closeGuard.isReleased();
    }

    @Override
    public void release() {
        if (!closeGuard.markReleased()) {
            YoBluetoothLogger.w(TAG, "YoBleRuntime 重复释放，已忽略");
            return;
        }
        YoBluetoothLogger.i(TAG, "正在释放 YoBleRuntime ...");
        eventHub.clear();
        shutdownExecutor(ioExecutor, "io");
        if (schedulerExecutor != null) {
            shutdownExecutor(schedulerExecutor, "scheduler");
        }
        if (callbackExecutor != null) {
            shutdownExecutor(callbackExecutor, "callback");
        }
        if (stateExecutor != null) {
            shutdownExecutor(stateExecutor, "state");
        }
        YoBluetoothLogger.i(TAG, "YoBleRuntime 释放完成");
    }

    private void shutdownExecutor(@NonNull ExecutorService executor, @NonNull String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                YoBluetoothLogger.w(TAG, name + " 执行器超时未结束，强制 shutdownNow");
                executor.shutdownNow();
                executor.awaitTermination(1L, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
