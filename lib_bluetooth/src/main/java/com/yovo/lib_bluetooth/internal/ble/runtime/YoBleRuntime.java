package com.yovo.lib_bluetooth.internal.ble.runtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoDispatcher;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoExecutors;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.diagnostics.BluetoothDiagnosticsBus;
import com.yovo.lib_bluetooth.internal.common.diagnostics.BluetoothEventBus;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoCloseGuard;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BLE 内部核心运行时：BLE 模块唯一的线程模型。
 *
 * <p>四个执行上下文：
 * <ul>
 *     <li>{@link #state(Runnable)}：单线程串行 state 线程。BLE 所有领域组件（会话、队列、订阅、配对、
 *     扫描、外设…）的可变状态只允许在该线程读写，组件内部因此不需要任何锁。</li>
 *     <li>{@link #callback(Runnable)}：用户回调派发（主线程或独立回调线程）。库内部状态绝不在此线程修改。</li>
 *     <li>{@link #io(Runnable)}：阻塞式 IO（L2CAP 建链等），不得触碰领域状态。</li>
 *     <li>{@link #scheduler()} / {@link #stateDelayed(Runnable, long)}：定时器；到期后必须回到 state 线程。</li>
 * </ul>
 *
 * <p>生命周期 ACTIVE → CLOSING → CLOSED：
 * {@link #release()} 不阻塞调用线程。进入 CLOSING 后 {@link #isReleased()} 立即为 {@code true}（门面据此拒绝新请求），
 * 但 state / callback 仍继续接收任务，保证各组件在释放前投递的清理任务以及清理任务产生的终态回调
 * （挂起操作失败、连接断开等）全部送达；待 state 线程排空后才关闭各执行器进入 CLOSED。
 */
public final class YoBleRuntime implements YoReleasable {

    private static final String TAG = "YoBleRuntime";
    /** 关闭阶段排空 state 队列的最大自投递轮数，防止外部持续投递导致无法收敛。 */
    private static final int MAX_DRAIN_ROUNDS = 16;
    private static final YoTimeoutHandle NOOP_HANDLE = () -> { };

    private final YoCloseGuard closeGuard = new YoCloseGuard();
    @NonNull
    private final ExecutorService stateExecutor;
    @NonNull
    private final ExecutorService ioExecutor;
    @Nullable
    private final ExecutorService callbackExecutor;
    @NonNull
    private final YoDispatcher callbackDispatcher;
    @NonNull
    private final ScheduledExecutorService schedulerExecutor;
    @NonNull
    private final YoTaskScheduler scheduler;
    @NonNull
    private final BluetoothEventBus eventBus = new BluetoothEventBus();
    /** 已提交但尚未执行完的 state 任务数，仅用于关闭阶段判断是否排空。 */
    private final AtomicInteger pendingStateTasks = new AtomicInteger();
    /** 当前 state 线程；由线程工厂在创建线程时同步写入。 */
    @Nullable
    private volatile Thread stateThread;

    public YoBleRuntime() {
        this(YoBleRuntimeConfig.builder().build());
    }

    public YoBleRuntime(@NonNull YoBleRuntimeConfig config) {
        final String stateThreadName = config.stateThreadName();
        this.stateExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, stateThreadName);
            stateThread = thread;
            return thread;
        });
        this.ioExecutor = Executors.newFixedThreadPool(config.ioThreadPoolSize(), numbered("yo-ble-io"));
        this.schedulerExecutor = Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, "yo-ble-scheduler"));
        final YoTaskScheduler rawScheduler = YoExecutors.scheduler(schedulerExecutor);
        this.scheduler = (task, delayMillis) -> {
            try {
                return rawScheduler.schedule(task, delayMillis);
            } catch (RejectedExecutionException closed) {
                return NOOP_HANDLE;
            }
        };
        if (config.callbackOnMainThread()) {
            this.callbackExecutor = null;
            this.callbackDispatcher = YoExecutors.mainDispatcher();
        } else {
            this.callbackExecutor = Executors.newSingleThreadExecutor(
                    runnable -> new Thread(runnable, "yo-ble-callback"));
            this.callbackDispatcher = callbackExecutor::execute;
        }
    }

    /** 投递到 state 线程串行执行；任务异常被隔离记录，不会杀死 state 线程。CLOSED 后投递被丢弃。 */
    public void state(@NonNull Runnable task) {
        pendingStateTasks.incrementAndGet();
        try {
            stateExecutor.execute(() -> runStateTask(task));
        } catch (RejectedExecutionException closed) {
            pendingStateTasks.decrementAndGet();
        }
    }

    /** 延时后回到 state 线程执行，返回可取消句柄。 */
    @NonNull
    public YoTimeoutHandle stateDelayed(@NonNull Runnable task, long delayMillis) {
        return scheduler.schedule(() -> state(task), delayMillis);
    }

    /** 投递阻塞式 IO 任务。 */
    public void io(@NonNull Runnable task) {
        try {
            ioExecutor.execute(task);
        } catch (RejectedExecutionException ignored) {
        }
    }

    /**
     * 向用户回调线程派发。用户代码抛出的异常不做吞没（按平台惯例由调用线程的未捕获处理器暴露），
     * 避免掩盖业务缺陷。
     */
    public void callback(@NonNull Runnable task) {
        try {
            callbackDispatcher.dispatch(task);
        } catch (RejectedExecutionException ignored) {
        }
    }

    @NonNull
    public YoTaskScheduler scheduler() {
        return scheduler;
    }

    @NonNull
    public BluetoothEventBus eventBus() {
        return eventBus;
    }

    @NonNull
    public BluetoothDiagnosticsBus diagnostics() {
        return BluetoothDiagnosticsBus.get();
    }

    public boolean isStateThread() {
        return Thread.currentThread() == stateThread;
    }

    /** 线程封闭断言：领域组件的状态方法入口调用。 */
    public void checkStateThread() {
        if (!isStateThread()) {
            throw new IllegalStateException(
                    "Must be called from BLE state thread, called from: " + Thread.currentThread().getName());
        }
    }

    /** CLOSING 或 CLOSED 时为 {@code true}。 */
    public boolean isReleased() {
        return closeGuard.isReleased();
    }

    @Override
    public void release() {
        if (!closeGuard.markReleased()) {
            return;
        }
        YoBtLogger.i(TAG, "YoBleRuntime 进入 CLOSING，等待 state 线程排空");
        state(() -> finishRelease(0));
    }

    private void runStateTask(@NonNull Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            BluetoothDiagnosticsBus.get().recordException(TAG, "state 线程任务异常（已隔离）", t);
        } finally {
            pendingStateTasks.decrementAndGet();
        }
    }

    /**
     * 在 state 线程执行：只要队列里还有其它任务（清理任务及其再投递的任务）就把自己排到队尾，
     * 排空后关闭全部执行器。已入队的回调 / IO 任务仍会执行完（shutdown 而非 shutdownNow）。
     */
    private void finishRelease(int round) {
        if (pendingStateTasks.get() > 1 && round < MAX_DRAIN_ROUNDS) {
            state(() -> finishRelease(round + 1));
            return;
        }
        eventBus.clear();
        schedulerExecutor.shutdownNow();
        ioExecutor.shutdown();
        if (callbackExecutor != null) {
            callbackExecutor.shutdown();
        }
        stateExecutor.shutdown();
        YoBtLogger.i(TAG, "YoBleRuntime 已 CLOSED");
    }

    @NonNull
    private static ThreadFactory numbered(@NonNull String prefix) {
        final AtomicInteger sequence = new AtomicInteger();
        return runnable -> new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
    }
}
