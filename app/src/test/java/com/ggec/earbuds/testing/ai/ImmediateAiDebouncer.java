package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.ai.settings.AiDebouncer;

/** 立即执行的节流器：单测里不需要真的等窗口。 */
public final class ImmediateAiDebouncer implements AiDebouncer {

    @Override
    public void schedule(long delayMillis, @NonNull Runnable task) {
        task.run();
    }

    @Override
    public void cancel() {
    }
}
