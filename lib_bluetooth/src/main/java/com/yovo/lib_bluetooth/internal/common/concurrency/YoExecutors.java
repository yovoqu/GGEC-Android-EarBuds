package com.yovo.lib_bluetooth.internal.common.concurrency;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙模块线程池与主线程调度工具类。
 */
public final class YoExecutors {
    private YoExecutors() {
    }

    @NonNull
    public static ExecutorService single(@NonNull String name) {
        return Executors.newSingleThreadExecutor(r -> new Thread(r, name));
    }

    @NonNull
    public static ExecutorService cached(@NonNull String name) {
        return Executors.newCachedThreadPool(r -> new Thread(r, name));
    }

    @NonNull
    public static ExecutorService fixed(@NonNull String name, int size) {
        int poolSize = Math.max(1, size);
        return Executors.newFixedThreadPool(poolSize, r -> new Thread(r, name));
    }

    @NonNull
    public static ScheduledExecutorService scheduled(@NonNull String name) {
        return Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name));
    }

    @NonNull
    public static YoDispatcher mainDispatcher() {
        Handler handler = new Handler(Looper.getMainLooper());
        return handler::post;
    }

    @NonNull
    public static YoTaskScheduler scheduler(@NonNull ScheduledExecutorService executor) {
        return (task, delayMillis) -> {
            java.util.concurrent.ScheduledFuture<?> future =
                    executor.schedule(task, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        };
    }
}
