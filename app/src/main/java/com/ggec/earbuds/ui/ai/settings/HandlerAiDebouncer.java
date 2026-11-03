package com.ggec.earbuds.ui.ai.settings;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

/**
 * 基于主线程 Handler 的节流器：最后一次输入后 {@code delayMillis} 才真正保存。
 * 只负责延迟，真正的仓库写入由 ViewModel 投到 IO 线程执行。
 */
public final class HandlerAiDebouncer implements AiDebouncer {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pending;

    @NonNull
    @Override
    public void schedule(long delayMillis, @NonNull Runnable task) {
        cancel();
        pending = () -> {
            pending = null;
            task.run();
        };
        handler.postDelayed(pending, Math.max(0L, delayMillis));
    }

    @Override
    public void cancel() {
        if (pending != null) {
            handler.removeCallbacks(pending);
            pending = null;
        }
    }
}
