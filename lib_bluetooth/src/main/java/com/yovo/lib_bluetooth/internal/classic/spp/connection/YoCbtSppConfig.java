package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionPolicy;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 连接配置，包含超时、重试次数、读写缓冲等参数。
 */

public final class YoCbtSppConfig {
    private final long connectTimeoutMillis;
    private final int maxConnectAttempts;
    private final long retryDelayMillis;
    private final boolean allowHiddenApiChannel;
    private final YoCbtSppConnectionPolicy connectionPolicy;
    private final int writeQueueCapacity;

    private YoCbtSppConfig(Builder builder) {
        this.connectTimeoutMillis = builder.connectTimeoutMillis <= 0 ? 12000L : builder.connectTimeoutMillis;
        this.maxConnectAttempts = builder.maxConnectAttempts <= 0 ? 1 : builder.maxConnectAttempts;
        this.retryDelayMillis = Math.max(0L, builder.retryDelayMillis);
        this.allowHiddenApiChannel = builder.allowHiddenApiChannel;
        this.connectionPolicy = builder.connectionPolicy != null
                ? builder.connectionPolicy
                : YoCbtSppConnectionPolicy.defaultUserSpecified();
        this.writeQueueCapacity = builder.writeQueueCapacity <= 0 ? 256 : builder.writeQueueCapacity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int getMaxConnectAttempts() {
        return maxConnectAttempts;
    }

    public long getRetryDelayMillis() {
        return retryDelayMillis;
    }

    public boolean isAllowHiddenApiChannel() {
        return allowHiddenApiChannel;
    }

    @NonNull
    public YoCbtSppConnectionPolicy getConnectionPolicy() {
        return connectionPolicy;
    }

    public int getWriteQueueCapacity() {
        return writeQueueCapacity;
    }

    public static final class Builder {
        private long connectTimeoutMillis;
        private int maxConnectAttempts = 1;
        private long retryDelayMillis;
        private boolean allowHiddenApiChannel;
        private YoCbtSppConnectionPolicy connectionPolicy;
        private int writeQueueCapacity = 256;

        public Builder connectTimeoutMillis(long value) {
            this.connectTimeoutMillis = value;
            return this;
        }

        public Builder maxConnectAttempts(int value) {
            this.maxConnectAttempts = value;
            return this;
        }

        public Builder retryDelayMillis(long value) {
            this.retryDelayMillis = value;
            return this;
        }

        public Builder allowHiddenApiChannel(boolean value) {
            this.allowHiddenApiChannel = value;
            return this;
        }

        public Builder connectionPolicy(@NonNull YoCbtSppConnectionPolicy value) {
            this.connectionPolicy = value;
            return this;
        }

        public Builder writeQueueCapacity(int value) {
            this.writeQueueCapacity = value;
            return this;
        }

        public YoCbtSppConfig build() {
            return new YoCbtSppConfig(this);
        }
    }
}
