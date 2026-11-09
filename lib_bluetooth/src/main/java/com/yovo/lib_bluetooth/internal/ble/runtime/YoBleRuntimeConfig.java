package com.yovo.lib_bluetooth.internal.ble.runtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoDispatcher;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;

import java.util.Objects;

/**
 * BLE 运行时配置：支持配置 state/IO 线程池、回调线程及单测注入自定义调度器。
 */
public final class YoBleRuntimeConfig {

    private final String stateThreadName;
    private final int ioThreadPoolSize;
    private final boolean callbackOnMainThread;
    private final long shutdownTimeoutSeconds;
    @Nullable
    private final YoDispatcher customStateDispatcher;
    @Nullable
    private final YoDispatcher customCallbackDispatcher;
    @Nullable
    private final YoTaskScheduler customScheduler;

    private YoBleRuntimeConfig(Builder builder) {
        this.stateThreadName = builder.stateThreadName;
        this.ioThreadPoolSize = builder.ioThreadPoolSize;
        this.callbackOnMainThread = builder.callbackOnMainThread;
        this.shutdownTimeoutSeconds = builder.shutdownTimeoutSeconds;
        this.customStateDispatcher = builder.customStateDispatcher;
        this.customCallbackDispatcher = builder.customCallbackDispatcher;
        this.customScheduler = builder.customScheduler;
    }

    @NonNull
    public String stateThreadName() {
        return stateThreadName;
    }

    public int ioThreadPoolSize() {
        return ioThreadPoolSize;
    }

    public boolean callbackOnMainThread() {
        return callbackOnMainThread;
    }

    public long shutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }

    @Nullable
    public YoDispatcher customStateDispatcher() {
        return customStateDispatcher;
    }

    @Nullable
    public YoDispatcher customCallbackDispatcher() {
        return customCallbackDispatcher;
    }

    @Nullable
    public YoTaskScheduler customScheduler() {
        return customScheduler;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String stateThreadName = "yo-ble-state";
        private int ioThreadPoolSize = 2;
        private boolean callbackOnMainThread = true;
        private long shutdownTimeoutSeconds = 3L;
        @Nullable
        private YoDispatcher customStateDispatcher;
        @Nullable
        private YoDispatcher customCallbackDispatcher;
        @Nullable
        private YoTaskScheduler customScheduler;

        @NonNull
        public Builder stateThreadName(@NonNull String stateThreadName) {
            this.stateThreadName = Objects.requireNonNull(stateThreadName, "stateThreadName");
            return this;
        }

        @NonNull
        public Builder ioThreadPoolSize(int ioThreadPoolSize) {
            this.ioThreadPoolSize = Math.max(1, ioThreadPoolSize);
            return this;
        }

        @NonNull
        public Builder callbackOnMainThread(boolean callbackOnMainThread) {
            this.callbackOnMainThread = callbackOnMainThread;
            return this;
        }

        @NonNull
        public Builder shutdownTimeoutSeconds(long shutdownTimeoutSeconds) {
            this.shutdownTimeoutSeconds = Math.max(1L, shutdownTimeoutSeconds);
            return this;
        }

        @NonNull
        public Builder customStateDispatcher(@Nullable YoDispatcher dispatcher) {
            this.customStateDispatcher = dispatcher;
            return this;
        }

        @NonNull
        public Builder customCallbackDispatcher(@Nullable YoDispatcher dispatcher) {
            this.customCallbackDispatcher = dispatcher;
            return this;
        }

        @NonNull
        public Builder customScheduler(@Nullable YoTaskScheduler scheduler) {
            this.customScheduler = scheduler;
            return this;
        }

        @NonNull
        public YoBleRuntimeConfig build() {
            return new YoBleRuntimeConfig(this);
        }
    }
}
