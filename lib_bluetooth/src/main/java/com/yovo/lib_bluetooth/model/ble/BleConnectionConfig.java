package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 单次 BLE GATT 连接配置。
 */
public final class BleConnectionConfig {

    private final boolean autoConnect;
    private final long connectTimeoutMillis;
    private final long operationTimeoutMillis;
    private final int maxRetryCount;
    private final long retryInitialDelayMillis;
    private final long retryMaxDelayMillis;
    private final long closeDelayMillis;
    private final long bondedDiscoveryDelayMillis;
    private final boolean autoDiscoverServices;
    @Nullable
    private final Integer requestMtu;
    @Nullable
    private final BlePhyConfig preferredPhy;
    @Nullable
    private final BleConnectionPriority preferredConnectionPriority;
    private final boolean autoHandleServiceChanged;
    private final boolean refreshCacheBeforeDiscovery;
    @NonNull
    private final BleBondPolicy bondPolicy;

    private BleConnectionConfig(Builder builder) {
        this.autoConnect = builder.autoConnect;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.operationTimeoutMillis = builder.operationTimeoutMillis;
        this.maxRetryCount = builder.maxRetryCount;
        this.retryInitialDelayMillis = builder.retryInitialDelayMillis;
        this.retryMaxDelayMillis = builder.retryMaxDelayMillis;
        this.closeDelayMillis = builder.closeDelayMillis;
        this.bondedDiscoveryDelayMillis = builder.bondedDiscoveryDelayMillis;
        this.autoDiscoverServices = builder.autoDiscoverServices;
        this.requestMtu = builder.requestMtu;
        this.preferredPhy = builder.preferredPhy;
        this.preferredConnectionPriority = builder.preferredConnectionPriority;
        this.autoHandleServiceChanged = builder.autoHandleServiceChanged;
        this.refreshCacheBeforeDiscovery = builder.refreshCacheBeforeDiscovery;
        this.bondPolicy = builder.bondPolicy;
    }

    public boolean autoConnect() {
        return autoConnect;
    }

    public long connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public long operationTimeoutMillis() {
        return operationTimeoutMillis;
    }

    public int maxRetryCount() {
        return maxRetryCount;
    }

    public long retryInitialDelayMillis() {
        return retryInitialDelayMillis;
    }

    public long retryMaxDelayMillis() {
        return retryMaxDelayMillis;
    }

    public long closeDelayMillis() {
        return closeDelayMillis;
    }

    public long bondedDiscoveryDelayMillis() {
        return bondedDiscoveryDelayMillis;
    }

    public boolean autoDiscoverServices() {
        return autoDiscoverServices;
    }

    @Nullable
    public Integer requestMtu() {
        return requestMtu;
    }

    @Nullable
    public BlePhyConfig preferredPhy() {
        return preferredPhy;
    }

    @Nullable
    public BleConnectionPriority preferredConnectionPriority() {
        return preferredConnectionPriority;
    }

    public boolean autoHandleServiceChanged() {
        return autoHandleServiceChanged;
    }

    public boolean refreshCacheBeforeDiscovery() {
        return refreshCacheBeforeDiscovery;
    }

    @NonNull
    public BleBondPolicy bondPolicy() {
        return bondPolicy;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean autoConnect;
        private long connectTimeoutMillis = 15_000L;
        private long operationTimeoutMillis = 10_000L;
        private int maxRetryCount = 2;
        private long retryInitialDelayMillis = 300L;
        private long retryMaxDelayMillis = 3_000L;
        private long closeDelayMillis = 200L;
        private long bondedDiscoveryDelayMillis = 600L;
        private boolean autoDiscoverServices = true;
        @Nullable
        private Integer requestMtu = 517;
        @Nullable
        private BlePhyConfig preferredPhy;
        @Nullable
        private BleConnectionPriority preferredConnectionPriority;
        private boolean autoHandleServiceChanged = true;
        private boolean refreshCacheBeforeDiscovery = false;
        private BleBondPolicy bondPolicy = BleBondPolicy.NONE;

        @NonNull
        public Builder autoConnect(boolean autoConnect) {
            this.autoConnect = autoConnect;
            return this;
        }

        @NonNull
        public Builder connectTimeoutMillis(long connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis <= 0L ? 0L : Math.max(500L, connectTimeoutMillis);
            return this;
        }

        @NonNull
        public Builder operationTimeoutMillis(long operationTimeoutMillis) {
            this.operationTimeoutMillis = Math.max(200L, operationTimeoutMillis);
            return this;
        }

        @NonNull
        public Builder maxRetryCount(int maxRetryCount) {
            this.maxRetryCount = Math.max(0, maxRetryCount);
            return this;
        }

        @NonNull
        public Builder retryInitialDelayMillis(long retryInitialDelayMillis) {
            this.retryInitialDelayMillis = Math.max(0L, retryInitialDelayMillis);
            return this;
        }

        @NonNull
        public Builder retryMaxDelayMillis(long retryMaxDelayMillis) {
            this.retryMaxDelayMillis = Math.max(0L, retryMaxDelayMillis);
            return this;
        }

        @NonNull
        public Builder closeDelayMillis(long closeDelayMillis) {
            this.closeDelayMillis = Math.max(0L, closeDelayMillis);
            return this;
        }

        @NonNull
        public Builder bondedDiscoveryDelayMillis(long bondedDiscoveryDelayMillis) {
            this.bondedDiscoveryDelayMillis = Math.max(0L, bondedDiscoveryDelayMillis);
            return this;
        }

        @NonNull
        public Builder autoDiscoverServices(boolean autoDiscoverServices) {
            this.autoDiscoverServices = autoDiscoverServices;
            return this;
        }

        @NonNull
        public Builder requestMtu(@Nullable Integer requestMtu) {
            this.requestMtu = requestMtu == null ? null : Math.max(23, Math.min(517, requestMtu));
            return this;
        }

        @NonNull
        public Builder preferredPhy(@Nullable BlePhyConfig preferredPhy) {
            this.preferredPhy = preferredPhy;
            return this;
        }

        @NonNull
        public Builder preferredConnectionPriority(@Nullable BleConnectionPriority priority) {
            this.preferredConnectionPriority = priority;
            return this;
        }

        @NonNull
        public Builder autoHandleServiceChanged(boolean autoHandleServiceChanged) {
            this.autoHandleServiceChanged = autoHandleServiceChanged;
            return this;
        }

        @NonNull
        public Builder refreshCacheBeforeDiscovery(boolean refreshCacheBeforeDiscovery) {
            this.refreshCacheBeforeDiscovery = refreshCacheBeforeDiscovery;
            return this;
        }

        @NonNull
        public Builder bondPolicy(@NonNull BleBondPolicy bondPolicy) {
            this.bondPolicy = bondPolicy;
            return this;
        }

        @NonNull
        public BleConnectionConfig build() {
            return new BleConnectionConfig(this);
        }
    }
}
