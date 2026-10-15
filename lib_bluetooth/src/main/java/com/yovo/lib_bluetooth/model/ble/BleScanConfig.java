package com.yovo.lib_bluetooth.model.ble;

/**
 * BLE 扫描配置。
 */
public final class BleScanConfig {

    private final long timeoutMillis;
    private final boolean requireConnectable;

    private BleScanConfig(Builder builder) {
        this.timeoutMillis = builder.timeoutMillis;
        this.requireConnectable = builder.requireConnectable;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public boolean requireConnectable() {
        return requireConnectable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long timeoutMillis = 12000L;
        private boolean requireConnectable;

        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = Math.max(1000L, timeoutMillis);
            return this;
        }

        public Builder requireConnectable(boolean requireConnectable) {
            this.requireConnectable = requireConnectable;
            return this;
        }

        public BleScanConfig build() {
            return new BleScanConfig(this);
        }
    }
}
