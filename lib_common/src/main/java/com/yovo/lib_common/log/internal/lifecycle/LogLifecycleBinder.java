package com.yovo.lib_common.log.internal.lifecycle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.yovo.lib_common.log.YoLog;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进后台 flush：进程级 ON_STOP 触发（ProcessLifecycleOwner），
 * 避免单个 Activity 切换时频繁刷盘。
 */
public final class LogLifecycleBinder {

    private static final String TAG = "LogLifecycleBinder";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    @Nullable
    private static volatile LifecycleEventObserver observer;

    private LogLifecycleBinder() {
    }

    public static void install(@NonNull Runnable onStop) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            observer = new LifecycleEventObserver() {
                @Override
                public void onStateChanged(@NonNull LifecycleOwner source,
                                           @NonNull Lifecycle.Event event) {
                    if (event == Lifecycle.Event.ON_STOP) {
                        onStop.run();
                    }
                }
            };
            ProcessLifecycleOwner.get().getLifecycle().addObserver(observer);
        } catch (RuntimeException e) {
            observer = null;
            INSTALLED.set(false);
            YoLog.w(TAG, "进程生命周期不可用，已禁用后台刷新", e);
        }
    }

    /** 卸载观察者，供 YoLog.shutdown / 重复 init 时调用。 */
    public static void uninstall() {
        if (!INSTALLED.compareAndSet(true, false)) {
            return;
        }
        LifecycleEventObserver local = observer;
        observer = null;
        if (local != null) {
            try {
                ProcessLifecycleOwner.get().getLifecycle().removeObserver(local);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
