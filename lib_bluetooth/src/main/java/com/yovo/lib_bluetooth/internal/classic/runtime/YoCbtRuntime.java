package com.yovo.lib_bluetooth.internal.classic.runtime;

import android.os.Looper;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoDispatcher;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoExecutors;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoCloseGuard;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 经典蓝牙运行时，提供 state/IO/回调线程池调度、任务调度与资源释放。
 */
public final class YoCbtRuntime {
    private static final String TAG = "YoCbtRuntime";

    private final long shutdownTimeoutSeconds;
    private final boolean callbackOnMainThread;
    private final String stateThreadName;
    private final YoCloseGuard closeGuard = new YoCloseGuard();
    private final ExecutorService stateExecutor;
    private final ExecutorService ioExecutor;
    private final ExecutorService callbackExecutor;
    private final ScheduledExecutorService schedulerExecutor;
    private final YoDispatcher callbackDispatcher;
    private final YoTaskScheduler scheduler;
    private final YoCbtEventHub eventHub = new YoCbtEventHub();

    // 在首次执行时捕获单线程 state 执行器线程，用于线程检查
    private final AtomicReference<Thread> stateThreadRef = new AtomicReference<>(null);
    private final AtomicReference<Thread> callbackThreadRef = new AtomicReference<>(null);

    public YoCbtRuntime() {
        this(YoCbtRuntimeConfig.builder().build());
    }

    public YoCbtRuntime(@NonNull YoCbtRuntimeConfig config) {
        this.callbackOnMainThread = config.isCallbackOnMainThread();
        this.stateThreadName = config.getStateThreadName();
        this.shutdownTimeoutSeconds = config.getShutdownTimeoutSeconds();
        this.stateExecutor = YoExecutors.single(stateThreadName);
        this.ioExecutor = YoExecutors.fixed("yo-cbt-io", config.getIoThreadPoolSize());
        this.schedulerExecutor = YoExecutors.scheduled("yo-cbt-scheduler");
        if (callbackOnMainThread) {
            this.callbackExecutor = null;
            this.callbackDispatcher = YoExecutors.mainDispatcher();
        } else {
            this.callbackExecutor = YoExecutors.single("yo-cbt-callback");
            this.callbackDispatcher = task -> callbackExecutor.execute(() -> {
                callbackThreadRef.compareAndSet(null, Thread.currentThread());
                task.run();
            });
        }
        this.scheduler = YoExecutors.scheduler(schedulerExecutor);
        stateExecutor.execute(() -> stateThreadRef.set(Thread.currentThread()));
    }

    // ── 线程池调度器 ──────────────────────────────────────

    /**
     * 将任务分派到单线程 state 执行器。任务按提交顺序串行执行。
     */
    public void state(@NonNull Runnable task) {
        if (!isReleased()) {
            stateExecutor.execute(task);
        }
    }

    /**
     * 将任务分派到 IO 线程池。适用于阻塞式 IO 操作，例如 socket 读取、文件访问或数据库查询。
     */
    public void io(@NonNull Runnable task) {
        if (!isReleased()) {
            ioExecutor.execute(task);
        }
    }

    /**
     * 通过回调调度器将任务分派到主（UI）线程。
     * 所有影响 UI 的结果与状态变更都必须通过此调度器。
     */
    public void callback(@NonNull Runnable task) {
        if (!isReleased()) {
            callbackDispatcher.dispatch(task);
        }
    }

    /**
     * 返回用于延迟一次性执行的任务调度器。调度器运行在其底层 scheduled 执行器线程上。
     */
    @NonNull
    public YoTaskScheduler scheduler() {
        return scheduler;
    }

    @NonNull
    public YoCbtEventHub eventHub() {
        return eventHub;
    }

    // ── 生命周期 ────────────────────────────────────────────────────

    public boolean isReleased() {
        return closeGuard.isReleased();
    }

    /**
     * 按正确顺序释放所有资源：
     * <ol>
     *   <li>IO 执行器 — 最先关闭（最不稳定，线程数无上限）</li>
     *   <li>Scheduled 执行器 — 取消待执行的延迟任务</li>
     *   <li>State 执行器 — 最后允许待处理 state 任务排空</li>
     * </ol>
     * 每个执行器都会获得配置的关闭超时时间，以便在强制终止前完成进行中的工作。
     * <p>
     * 本方法是幂等的 — 后续调用会被忽略。
     */
    public void release() {
        if (!closeGuard.markReleased()) {
            YoBluetoothLogger.w(TAG, "release() 被重复调用——已忽略");
            return;
        }

        YoBluetoothLogger.i(TAG, "正在释放 YoCbtRuntime ...");

        eventHub.clear();
        shutdownExecutor(ioExecutor, "io");
        shutdownExecutor(schedulerExecutor, "scheduler");
        if (callbackExecutor != null) {
            shutdownExecutor(callbackExecutor, "callback");
        }
        shutdownExecutor(stateExecutor, "state");

        YoBluetoothLogger.i(TAG, "YoCbtRuntime 释放成功");
    }

    private void shutdownExecutor(@NonNull ExecutorService executor, @NonNull String name) {
        YoBluetoothLogger.d(TAG, "正在关闭 " + name + " 执行器 ...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                YoBluetoothLogger.w(TAG, name + " 执行器未在 "
                        + shutdownTimeoutSeconds + " 秒内终止——强制关闭");
                executor.shutdownNow();
                if (!executor.awaitTermination(1L, TimeUnit.SECONDS)) {
                    YoBluetoothLogger.e(TAG, name + " 执行器在 shutdownNow() 后仍未能终止");
                }
            } else {
                YoBluetoothLogger.d(TAG, name + " 执行器已正常终止");
            }
        } catch (InterruptedException e) {
            YoBluetoothLogger.e(TAG, "等待 " + name + " 执行器关闭时被中断", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ── 线程检查 ──────────────────────────────────────────────

    /**
     * 断言当前线程为单线程 state 执行器线程。
     *
     * @throws IllegalStateException 若从其他线程调用
     */
    public void checkStateThread() {
        Thread current = Thread.currentThread();
        Thread expected = stateThreadRef.get();
        if (expected == null) {
            if (!current.getName().startsWith(stateThreadName)) {
                throw new IllegalStateException(
                        "Must be called from state thread, but called from: " + current.getName());
            }
        } else if (current != expected) {
            throw new IllegalStateException(
                    "Must be called from state thread (" + expected.getName()
                            + "), but called from: " + current.getName());
        }
    }

    /**
     * 断言当前线程属于 IO 线程池。
     * <p>
     * 固定 IO 线程池中的所有线程均以 {@code "yo-cbt-io"} 为名称前缀。
     *
     * @throws IllegalStateException 若从不带此前缀的线程调用
     */
    public void checkIoThread() {
        Thread current = Thread.currentThread();
        if (!current.getName().startsWith("yo-cbt-io")) {
            throw new IllegalStateException(
                    "Must be called from IO thread, but called from: " + current.getName());
        }
    }

    /**
     * 断言当前线程为主（UI / 回调）线程。
     *
     * @throws IllegalStateException 若从其他线程调用
     */
    public void checkCallbackThread() {
        if (callbackOnMainThread) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Thread current = Thread.currentThread();
                throw new IllegalStateException(
                        "Must be called from main thread, but called from: " + current.getName());
            }
            return;
        }
        Thread current = Thread.currentThread();
        Thread expected = callbackThreadRef.get();
        if (expected == null) {
            if (!current.getName().startsWith("yo-cbt-callback")) {
                throw new IllegalStateException(
                        "Must be called from callback thread, but called from: " + current.getName());
            }
        } else if (current != expected) {
            throw new IllegalStateException(
                    "Must be called from callback thread (" + expected.getName()
                            + "), but called from: " + current.getName());
        }
    }

    // ── 作用域 ────────────────────────────────────────────────────────

    /**
     * 创建绑定到单次操作的作用域生命周期。通过返回的作用域提交的任务，
     * 在作用域释放后会被静默丢弃。适用于在生命周期边界取消进行中的操作，
     * 而不影响其他并发操作。
     * <p>
     * 用法：
     * <pre>{@code
     * YoCbtRuntimeScope scope = runtime.createScope();
     * try {
     *     scope.state(() -> { ... });
     *     scope.io(() -> { ... });
     *     scope.callback(() -> { ... });
     * } finally {
     *     scope.release();
     * }
     * }</pre>
     */
    @NonNull
    public YoCbtRuntimeScope createScope() {
        return new YoCbtRuntimeScope(this);
    }
}
