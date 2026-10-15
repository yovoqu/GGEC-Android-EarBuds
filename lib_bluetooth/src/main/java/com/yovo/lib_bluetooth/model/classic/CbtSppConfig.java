package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * SPP 连接配置。
 * <p>
 * 公开 {@code connectSpp} 始终携带调用方指定的端点，因此默认策略为
 * {@link CbtSppConnectionStrategy#USER_SPECIFIED_ONLY}。
 * 产测等固定 RFCOMM 通道场景应显式使用 {@link CbtSppConnectionStrategy#CHANNEL_ONLY}。
 */
public final class CbtSppConfig {

    private final long connectTimeoutMillis;
    private final int maxConnectAttempts;
    private final long retryDelayMillis;
    private final boolean allowHiddenApiChannel;
    @NonNull
    private final CbtSppConnectionStrategy connectionStrategy;

    private CbtSppConfig(Builder builder) {
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.maxConnectAttempts = builder.maxConnectAttempts;
        this.retryDelayMillis = builder.retryDelayMillis;
        this.allowHiddenApiChannel = builder.allowHiddenApiChannel;
        this.connectionStrategy = builder.connectionStrategy != null
                ? builder.connectionStrategy
                : CbtSppConnectionStrategy.USER_SPECIFIED_ONLY;
    }

    public long connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int maxConnectAttempts() {
        return maxConnectAttempts;
    }

    public long retryDelayMillis() {
        return retryDelayMillis;
    }

    public boolean allowHiddenApiChannel() {
        return allowHiddenApiChannel;
    }

    @NonNull
    public CbtSppConnectionStrategy connectionStrategy() {
        return connectionStrategy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long connectTimeoutMillis = 15000L;
        private int maxConnectAttempts = 3;
        private long retryDelayMillis = 1000L;
        private boolean allowHiddenApiChannel;
        private CbtSppConnectionStrategy connectionStrategy =
                CbtSppConnectionStrategy.USER_SPECIFIED_ONLY;

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

        public Builder connectionStrategy(@NonNull CbtSppConnectionStrategy value) {
            this.connectionStrategy = value;
            return this;
        }

        public CbtSppConfig build() {
            return new CbtSppConfig(this);
        }
    }
}
