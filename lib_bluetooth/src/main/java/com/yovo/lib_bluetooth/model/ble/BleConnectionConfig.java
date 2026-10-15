package com.yovo.lib_bluetooth.model.ble;

/**
 * BLE GATT 连接配置。
 */
public final class BleConnectionConfig {

    private final boolean autoConnect;
    private final long operationTimeoutMillis;

    private BleConnectionConfig(Builder builder) {
        this.autoConnect = builder.autoConnect;
        this.operationTimeoutMillis = builder.operationTimeoutMillis;
    }

    public boolean autoConnect() {
        return autoConnect;
    }

    public long operationTimeoutMillis() {
        return operationTimeoutMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean autoConnect;
        private long operationTimeoutMillis = 10000L;

        public Builder autoConnect(boolean autoConnect) {
            this.autoConnect = autoConnect;
            return this;
        }

        public Builder operationTimeoutMillis(long operationTimeoutMillis) {
            this.operationTimeoutMillis = Math.max(1000L, operationTimeoutMillis);
            return this;
        }

        public BleConnectionConfig build() {
            return new BleConnectionConfig(this);
        }
    }
}
