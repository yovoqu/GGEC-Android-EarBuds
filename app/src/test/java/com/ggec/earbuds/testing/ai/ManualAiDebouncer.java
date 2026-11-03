package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.ui.ai.settings.AiDebouncer;

/** 手动推进的节流器：验证"一串改动只落盘一次"。 */
public final class ManualAiDebouncer implements AiDebouncer {

    @Nullable
    private Runnable pending;

    @Override
    public void schedule(long delayMillis, @NonNull Runnable task) {
        pending = task;
    }

    @Override
    public void cancel() {
        pending = null;
    }

    public void runPending() {
        Runnable task = pending;
        pending = null;
        if (task != null) {
            task.run();
        }
    }
}
