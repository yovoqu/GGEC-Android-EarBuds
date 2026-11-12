package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 客户端全局配置。
 */
public final class BleConfig {

    private final int defaultMtu;
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
            if (defaultMtu < 23 || defaultMtu > 517) {
                throw new IllegalArgumentException(
                        "defaultMtu must be within [23, 517], but was " + defaultMtu);
            }
            this.defaultMtu = defaultMtu;
            return this;
        }

        @NonNull
        public Builder defaultOperationTimeoutMillis(long millis) {
            if (millis < 500L) {
                throw new IllegalArgumentException(
                        "defaultOperationTimeoutMillis must be >= 500, but was " + millis);
            }
            this.defaultOperationTimeoutMillis = millis;
            return this;
        }

        @NonNull
        public Builder gattOperationQueueCapacity(int capacity) {
            if (capacity < 8) {
                throw new IllegalArgumentException(
                        "gattOperationQueueCapacity must be >= 8, but was " + capacity);
            }
            this.gattOperationQueueCapacity = capacity;
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
            if (capacity < 32) {
                throw new IllegalArgumentException(
                        "diagnosticsTimelineCapacity must be >= 32, but was " + capacity);
            }
            this.diagnosticsTimelineCapacity = capacity;
            return this;
        }

        @NonNull
        public Builder scanThrottleWindowMillis(long windowMillis) {
            if (windowMillis <= 0L) {
                throw new IllegalArgumentException(
                        "scanThrottleWindowMillis must be > 0, but was " + windowMillis);
            }
            this.scanThrottleWindowMillis = windowMillis;
            return this;
        }

        @NonNull
        public Builder scanThrottleMaxStarts(int maxStarts) {
            if (maxStarts <= 0) {
                throw new IllegalArgumentException(
                        "scanThrottleMaxStarts must be > 0, but was " + maxStarts);
            }
            this.scanThrottleMaxStarts = maxStarts;
            return this;
        }

        @NonNull
        public BleConfig build() {
            return new BleConfig(this);
        }
    }
}
