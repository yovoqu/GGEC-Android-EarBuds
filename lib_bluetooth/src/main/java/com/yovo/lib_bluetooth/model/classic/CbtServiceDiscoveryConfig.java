package com.yovo.lib_bluetooth.model.classic;

/**
 * SDP 服务发现配置。
 */
public final class CbtServiceDiscoveryConfig {

    private final long timeoutMillis;

    private CbtServiceDiscoveryConfig(Builder builder) {
        this.timeoutMillis = builder.timeoutMillis <= 0 ? 10000L : builder.timeoutMillis;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long timeoutMillis;

        public Builder timeoutMillis(long value) {
            this.timeoutMillis = value;
            return this;
        }

        public CbtServiceDiscoveryConfig build() {
            return new CbtServiceDiscoveryConfig(this);
        }
    }
}
