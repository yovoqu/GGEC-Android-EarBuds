package com.ggec.earbuds.ui.ai.common;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 把"启动时读一次"和"之后的变化通知"合成一条不会倒退的设置流：
 * 读盘在后台进行，读完之前如果已经收到了更新的通知，读到的旧值就丢掉。
 * 读盘失败时通知 {@code onLoadFailure}，之后的变化通知照常送达。
 * 回调之间互斥执行，所以 {@code sink} 看到的顺序就是设置真实变化的顺序。
 */
public final class AiSettingsFeed {

    private final Object lock = new Object();
    private final Consumer<AiSettings> sink;
    private final Consumer<AiFailure> onLoadFailure;
    private final Subscription subscription;
    private boolean observed;
    private boolean closed;

    public AiSettingsFeed(
            LoadAiSettings load,
            ObserveAiSettings observe,
            Executor io,
            Consumer<AiSettings> sink,
            Consumer<AiFailure> onLoadFailure) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.onLoadFailure = Objects.requireNonNull(onLoadFailure, "onLoadFailure");
        Objects.requireNonNull(load, "load");
        this.subscription = Objects.requireNonNull(observe, "observe").execute(this::onChanged);
        Objects.requireNonNull(io, "io").execute(() -> {
            try {
                onInitial(load.execute());
            } catch (AiFailure failure) {
                onInitialFailure(failure);
            }
        });
    }

    public void close() {
        synchronized (lock) {
            closed = true;
        }
        subscription.cancel();
    }

    private void onChanged(AiSettings settings) {
        synchronized (lock) {
            observed = true;
            if (!closed) {
                sink.accept(settings);
            }
        }
    }

    private void onInitial(AiSettings settings) {
        synchronized (lock) {
            if (!observed && !closed) {
                sink.accept(settings);
            }
        }
    }

    private void onInitialFailure(AiFailure failure) {
        synchronized (lock) {
            if (!observed && !closed) {
                onLoadFailure.accept(failure);
            }
        }
    }
}
