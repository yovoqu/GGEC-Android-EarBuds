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
 *
 * <p>安装状态属于本实例：由日志核心实例创建并持有，不落静态可写字段。</p>
 */
public final class LogLifecycleBinder {

    private static final String TAG = "LogLifecycleBinder";

    private final AtomicBoolean installed = new AtomicBoolean();

    @Nullable
    private LifecycleEventObserver observer;

    public LogLifecycleBinder() {
    }

    public void install(@NonNull Runnable onStop) {
        if (!installed.compareAndSet(false, true)) {
            return;
        }
        LifecycleEventObserver installing = new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull LifecycleOwner source,
                                       @NonNull Lifecycle.Event event) {
                if (event == Lifecycle.Event.ON_STOP) {
                    onStop.run();
                }
            }
        };
        observer = installing;
        try {
            ProcessLifecycleOwner.get().getLifecycle().addObserver(installing);
        } catch (RuntimeException e) {
            observer = null;
            installed.set(false);
            YoLog.w(TAG, "进程生命周期不可用，已禁用后台刷新", e);
        }
    }

    /** 卸载观察者，供 YoLog.shutdown / 重复 init 时调用。 */
    public void uninstall() {
        if (!installed.compareAndSet(true, false)) {
            return;
        }
        LifecycleEventObserver local = observer;
        observer = null;
        if (local != null) {
            try {
                ProcessLifecycleOwner.get().getLifecycle().removeObserver(local);
            } catch (RuntimeException e) {
                YoLog.w(TAG, "移除进程生命周期观察者失败", e);
            }
        }
    }
}
