package com.yovo.lib_bluetooth.model.classic;

/**
 * 设备发现配置。
 */
public final class CbtDiscoveryConfig {

    private final long timeoutMillis;
    private final boolean requireName;

    private CbtDiscoveryConfig(Builder builder) {
        this.timeoutMillis = builder.timeoutMillis <= 0 ? 12000L : builder.timeoutMillis;
        this.requireName = builder.requireName;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public boolean requireName() {
        return requireName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long timeoutMillis;
        private boolean requireName;

        public Builder timeoutMillis(long value) {
            this.timeoutMillis = value;
            return this;
        }

        public Builder requireName(boolean value) {
            this.requireName = value;
            return this;
        }

        public CbtDiscoveryConfig build() {
            return new CbtDiscoveryConfig(this);
        }
    }
}
