package com.yovo.lib_bluetooth.model.ble;

/**
 * BLE 客户端全局配置。
 */
public final class BleConfig {

    private final int defaultMtu;
    private final long defaultScanTimeoutMillis;
    private final long defaultOperationTimeoutMillis;

    private BleConfig(Builder builder) {
        this.defaultMtu = builder.defaultMtu;
        this.defaultScanTimeoutMillis = builder.defaultScanTimeoutMillis;
        this.defaultOperationTimeoutMillis = builder.defaultOperationTimeoutMillis;
    }

    public int defaultMtu() {
        return defaultMtu;
    }

    public long defaultScanTimeoutMillis() {
        return defaultScanTimeoutMillis;
    }

    public long defaultOperationTimeoutMillis() {
        return defaultOperationTimeoutMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int defaultMtu = 512;
        private long defaultScanTimeoutMillis = 12000L;
        private long defaultOperationTimeoutMillis = 10000L;

        public Builder defaultMtu(int defaultMtu) {
            this.defaultMtu = Math.max(23, defaultMtu);
            return this;
        }

        public Builder defaultScanTimeoutMillis(long millis) {
            this.defaultScanTimeoutMillis = Math.max(1000L, millis);
            return this;
        }

        public Builder defaultOperationTimeoutMillis(long millis) {
            this.defaultOperationTimeoutMillis = Math.max(1000L, millis);
            return this;
        }

        public BleConfig build() {
            return new BleConfig(this);
        }
    }
}
