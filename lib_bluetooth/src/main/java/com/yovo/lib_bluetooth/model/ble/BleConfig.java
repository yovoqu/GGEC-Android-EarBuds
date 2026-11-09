package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 客户端全局配置。
 */
public final class BleConfig {

    private final int defaultMtu;
    private final long defaultScanTimeoutMillis;
    private final long defaultOperationTimeoutMillis;
    private final int gattOperationQueueCapacity;
    private final boolean callbackOnMainThread;
    private final boolean allowHiddenApiCacheRefresh;
    private final boolean allowHiddenApiBondRemoval;
    private final int diagnosticsTimelineCapacity;
    private final long scanThrottleWindowMillis;
    private final int scanThrottleMaxStarts;

    private BleConfig(Builder builder) {
        this.defaultMtu = builder.defaultMtu;
        this.defaultScanTimeoutMillis = builder.defaultScanTimeoutMillis;
        this.defaultOperationTimeoutMillis = builder.defaultOperationTimeoutMillis;
        this.gattOperationQueueCapacity = builder.gattOperationQueueCapacity;
        this.callbackOnMainThread = builder.callbackOnMainThread;
        this.allowHiddenApiCacheRefresh = builder.allowHiddenApiCacheRefresh;
        this.allowHiddenApiBondRemoval = builder.allowHiddenApiBondRemoval;
        this.diagnosticsTimelineCapacity = builder.diagnosticsTimelineCapacity;
        this.scanThrottleWindowMillis = builder.scanThrottleWindowMillis;
        this.scanThrottleMaxStarts = builder.scanThrottleMaxStarts;
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

    public int gattOperationQueueCapacity() {
        return gattOperationQueueCapacity;
    }

    public boolean callbackOnMainThread() {
        return callbackOnMainThread;
    }

    public boolean allowHiddenApiCacheRefresh() {
        return allowHiddenApiCacheRefresh;
    }

    public boolean allowHiddenApiBondRemoval() {
        return allowHiddenApiBondRemoval;
    }

    public int diagnosticsTimelineCapacity() {
        return diagnosticsTimelineCapacity;
    }

    public long scanThrottleWindowMillis() {
        return scanThrottleWindowMillis;
    }

    public int scanThrottleMaxStarts() {
        return scanThrottleMaxStarts;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int defaultMtu = 517;
        private long defaultScanTimeoutMillis = 12_000L;
        private long defaultOperationTimeoutMillis = 10_000L;
        private int gattOperationQueueCapacity = 128;
        private boolean callbackOnMainThread = true;
        private boolean allowHiddenApiCacheRefresh = false;
        private boolean allowHiddenApiBondRemoval = false;
        private int diagnosticsTimelineCapacity = 256;
        private long scanThrottleWindowMillis = 30_000L;
        private int scanThrottleMaxStarts = 5;

        @NonNull
        public Builder defaultMtu(int defaultMtu) {
            this.defaultMtu = Math.max(23, Math.min(517, defaultMtu));
            return this;
        }

        @NonNull
        public Builder defaultScanTimeoutMillis(long millis) {
            this.defaultScanTimeoutMillis = Math.max(1_000L, millis);
            return this;
        }

        @NonNull
        public Builder defaultOperationTimeoutMillis(long millis) {
            this.defaultOperationTimeoutMillis = Math.max(500L, millis);
            return this;
        }

        @NonNull
        public Builder gattOperationQueueCapacity(int capacity) {
            this.gattOperationQueueCapacity = Math.max(8, capacity);
            return this;
        }

        @NonNull
        public Builder callbackOnMainThread(boolean callbackOnMainThread) {
            this.callbackOnMainThread = callbackOnMainThread;
            return this;
        }

        @NonNull
        public Builder allowHiddenApiCacheRefresh(boolean allow) {
            this.allowHiddenApiCacheRefresh = allow;
            return this;
        }

        @NonNull
        public Builder allowHiddenApiBondRemoval(boolean allow) {
            this.allowHiddenApiBondRemoval = allow;
            return this;
        }

        @NonNull
        public Builder diagnosticsTimelineCapacity(int capacity) {
            this.diagnosticsTimelineCapacity = Math.max(32, capacity);
            return this;
        }

        @NonNull
        public Builder scanThrottleWindowMillis(long windowMillis) {
            this.scanThrottleWindowMillis = Math.max(1_000L, windowMillis);
            return this;
        }

        @NonNull
        public Builder scanThrottleMaxStarts(int maxStarts) {
            this.scanThrottleMaxStarts = Math.max(1, maxStarts);
            return this;
        }

        @NonNull
        public BleConfig build() {
            return new BleConfig(this);
        }
    }
}
