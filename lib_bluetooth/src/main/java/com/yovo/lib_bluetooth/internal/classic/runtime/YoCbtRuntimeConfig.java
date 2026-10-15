package com.yovo.lib_bluetooth.internal.classic.runtime;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtRuntime} 配置项，包含 state/IO 线程池、回调线程与关闭超时等参数。
 */
public final class YoCbtRuntimeConfig {

    private final String stateThreadName;
    private final int ioThreadPoolSize;
    private final boolean callbackOnMainThread;
    private final long shutdownTimeoutSeconds;

    private YoCbtRuntimeConfig(Builder builder) {
        this.stateThreadName = Objects.requireNonNull(builder.stateThreadName, "stateThreadName");
        this.ioThreadPoolSize = builder.ioThreadPoolSize;
        this.callbackOnMainThread = builder.callbackOnMainThread;
        this.shutdownTimeoutSeconds = builder.shutdownTimeoutSeconds;
    }

    // ── 访问器 ───────────────────────────────────────────────────

    /**
     * 返回单线程 state 执行器的线程名前缀。
     */
    @NonNull
    public String getStateThreadName() {
        return stateThreadName;
    }

    /**
     * 返回 IO 线程池大小。
     */
    public int getIoThreadPoolSize() {
        return ioThreadPoolSize;
    }

    /**
     * 返回回调是否分派到主线程。
     */
    public boolean isCallbackOnMainThread() {
        return callbackOnMainThread;
    }

    /**
     * 返回执行器优雅关闭的超时时间（秒）。
     */
    public long getShutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }

    // ── 构建器 ───────────────────────────────────────────────────

    /**
     * 创建带默认值的 {@link Builder}。
     */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String stateThreadName = "yo-cbt-state";
        private int ioThreadPoolSize = 4;
        private boolean callbackOnMainThread = true;
        private long shutdownTimeoutSeconds = 3L;

        private Builder() {
        }

        /**
         * 设置单线程 state 执行器的线程名前缀。
         */
        @NonNull
        public Builder stateThreadName(@NonNull String stateThreadName) {
            this.stateThreadName = stateThreadName;
            return this;
        }

        /**
         * 设置 IO 线程池大小，至少为 1。
         */
        @NonNull
        public Builder ioThreadPoolSize(int ioThreadPoolSize) {
            this.ioThreadPoolSize = Math.max(1, ioThreadPoolSize);
            return this;
        }

        /**
         * 设置回调是否分派到主线程。
         */
        @NonNull
        public Builder callbackOnMainThread(boolean callbackOnMainThread) {
            this.callbackOnMainThread = callbackOnMainThread;
            return this;
        }

        /**
         * 设置执行器优雅关闭超时（秒），至少为 1。
         */
        @NonNull
        public Builder shutdownTimeoutSeconds(long shutdownTimeoutSeconds) {
            this.shutdownTimeoutSeconds = Math.max(1L, shutdownTimeoutSeconds);
            return this;
        }

        /**
         * 构建配置实例。
         */
        @NonNull
        public YoCbtRuntimeConfig build() {
            return new YoCbtRuntimeConfig(this);
        }
    }

    // ── Object ────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtRuntimeConfig)) return false;
        YoCbtRuntimeConfig that = (YoCbtRuntimeConfig) o;
        return ioThreadPoolSize == that.ioThreadPoolSize
                && callbackOnMainThread == that.callbackOnMainThread
                && shutdownTimeoutSeconds == that.shutdownTimeoutSeconds
                && stateThreadName.equals(that.stateThreadName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(stateThreadName, ioThreadPoolSize,
                callbackOnMainThread, shutdownTimeoutSeconds);
    }

    @NonNull
    @Override
    public String toString() {
        return "YoCbtRuntimeConfig{"
                + "stateThreadName='" + stateThreadName + '\''
                + ", ioThreadPoolSize=" + ioThreadPoolSize
                + ", callbackOnMainThread=" + callbackOnMainThread
                + ", shutdownTimeoutSeconds=" + shutdownTimeoutSeconds
                + '}';
    }
}
