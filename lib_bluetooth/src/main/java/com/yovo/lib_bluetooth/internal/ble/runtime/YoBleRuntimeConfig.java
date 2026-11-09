package com.yovo.lib_bluetooth.internal.ble.runtime;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * BLE 运行时配置：统一配置 state / IO 线程池、回调派发线程策略与关闭超时。
 */
public final class YoBleRuntimeConfig {

    private final String stateThreadName;
    private final int ioThreadPoolSize;
    private final boolean callbackOnMainThread;
    private final long shutdownTimeoutSeconds;

    private YoBleRuntimeConfig(Builder builder) {
        this.stateThreadName = builder.stateThreadName;
        this.ioThreadPoolSize = builder.ioThreadPoolSize;
        this.callbackOnMainThread = builder.callbackOnMainThread;
        this.shutdownTimeoutSeconds = builder.shutdownTimeoutSeconds;
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

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String stateThreadName = "yo-ble-state";
        private int ioThreadPoolSize = 2;
        private boolean callbackOnMainThread = true;
        private long shutdownTimeoutSeconds = 3L;

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
        public YoBleRuntimeConfig build() {
            return new YoBleRuntimeConfig(this);
        }
    }
}
