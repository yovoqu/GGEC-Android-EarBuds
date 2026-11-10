package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * BLE 扫描配置（支持 ScanMode / CallbackType / Batching / MatchMode / PHY / Filters / 限流策略）。
 */
public final class BleScanConfig {

    private final long timeoutMillis;
    private final boolean requireConnectable;
    @NonNull
    private final BleScanMode scanMode;
    @NonNull
    private final BleScanCallbackType callbackType;
    @NonNull
    private final BleScanMatchMode matchMode;
    private final int numOfMatches;
    private final long reportDelayMillis;
    private final boolean legacyOnly;
    @NonNull
    private final BlePhy phy;
    private final boolean locationExempt;
    private final long deduplicationWindowMillis;
    private final long matchLostTimeoutMillis;
    @NonNull
    private final BleScanThrottlePolicy throttlePolicy;
    @NonNull
    private final List<BleScanFilter> filters;

    private BleScanConfig(Builder builder) {
        this.timeoutMillis = builder.timeoutMillis;
        this.requireConnectable = builder.requireConnectable;
        this.scanMode = builder.scanMode;
        this.callbackType = builder.callbackType;
        this.matchMode = builder.matchMode;
        this.numOfMatches = builder.numOfMatches;
        this.reportDelayMillis = builder.reportDelayMillis;
        this.legacyOnly = builder.legacyOnly;
        this.phy = builder.phy;
        this.locationExempt = builder.locationExempt;
        this.deduplicationWindowMillis = builder.deduplicationWindowMillis;
        this.matchLostTimeoutMillis = builder.matchLostTimeoutMillis;
        this.throttlePolicy = builder.throttlePolicy;
        this.filters = Collections.unmodifiableList(new ArrayList<>(builder.filters));
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public boolean requireConnectable() {
        return requireConnectable;
    }

    @NonNull
    public BleScanMode scanMode() {
        return scanMode;
    }

    @NonNull
    public BleScanCallbackType callbackType() {
        return callbackType;
    }

    @NonNull
    public BleScanMatchMode matchMode() {
        return matchMode;
    }

    public int numOfMatches() {
        return numOfMatches;
    }

    public long reportDelayMillis() {
        return reportDelayMillis;
    }

    public boolean legacyOnly() {
        return legacyOnly;
    }

    @NonNull
    public BlePhy phy() {
        return phy;
    }

    /** Manifest 已配置 neverForLocation 且业务仅寻找设备（无位置诉求）时为 true（默认 true）。 */
    public boolean locationExempt() {
        return locationExempt;
    }

    public long deduplicationWindowMillis() {
        return deduplicationWindowMillis;
    }

    public long matchLostTimeoutMillis() {
        return matchLostTimeoutMillis;
    }

    @NonNull
    public BleScanThrottlePolicy throttlePolicy() {
        return throttlePolicy;
    }

    @NonNull
    public List<BleScanFilter> filters() {
        return filters;
    }

    @NonNull
    public BleScanConfig withReportDelayMillis(long reportDelayMillis) {
        return new Builder(this).reportDelayMillis(reportDelayMillis).build();
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long timeoutMillis = 12_000L;
        private boolean requireConnectable;
        private BleScanMode scanMode = BleScanMode.LOW_LATENCY;
        private BleScanCallbackType callbackType = BleScanCallbackType.ALL_MATCHES;
        private BleScanMatchMode matchMode = BleScanMatchMode.AGGRESSIVE;
        private int numOfMatches = 3;
        private long reportDelayMillis = 0L;
        private boolean legacyOnly = true;
        private BlePhy phy = BlePhy.ALL_SUPPORTED;
        private boolean locationExempt = true;
        private long deduplicationWindowMillis = 0L;
        private long matchLostTimeoutMillis = 10_000L;
        private BleScanThrottlePolicy throttlePolicy = BleScanThrottlePolicy.DELAY_UNTIL_WINDOW_FREE;
        private final List<BleScanFilter> filters = new ArrayList<>();

        public Builder() {
        }

        private Builder(@NonNull BleScanConfig source) {
            this.timeoutMillis = source.timeoutMillis;
            this.requireConnectable = source.requireConnectable;
            this.scanMode = source.scanMode;
            this.callbackType = source.callbackType;
            this.matchMode = source.matchMode;
            this.numOfMatches = source.numOfMatches;
            this.reportDelayMillis = source.reportDelayMillis;
            this.legacyOnly = source.legacyOnly;
            this.phy = source.phy;
            this.locationExempt = source.locationExempt;
            this.deduplicationWindowMillis = source.deduplicationWindowMillis;
            this.matchLostTimeoutMillis = source.matchLostTimeoutMillis;
            this.throttlePolicy = source.throttlePolicy;
            this.filters.addAll(source.filters);
        }

        @NonNull
        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis <= 0L ? 0L : Math.max(200L, timeoutMillis);
            return this;
        }

        @NonNull
        public Builder requireConnectable(boolean requireConnectable) {
            this.requireConnectable = requireConnectable;
            return this;
        }

        @NonNull
        public Builder scanMode(@NonNull BleScanMode scanMode) {
            this.scanMode = Objects.requireNonNull(scanMode, "scanMode");
            return this;
        }

        @NonNull
        public Builder callbackType(@NonNull BleScanCallbackType callbackType) {
            this.callbackType = Objects.requireNonNull(callbackType, "callbackType");
            return this;
        }

        @NonNull
        public Builder matchMode(@NonNull BleScanMatchMode matchMode) {
            this.matchMode = Objects.requireNonNull(matchMode, "matchMode");
            return this;
        }

        @NonNull
        public Builder numOfMatches(int numOfMatches) {
            this.numOfMatches = Math.max(1, Math.min(3, numOfMatches));
            return this;
        }

        @NonNull
        public Builder reportDelayMillis(long reportDelayMillis) {
            this.reportDelayMillis = Math.max(0L, reportDelayMillis);
            return this;
        }

        @NonNull
        public Builder legacyOnly(boolean legacyOnly) {
            this.legacyOnly = legacyOnly;
            return this;
        }

        @NonNull
        public Builder phy(@NonNull BlePhy phy) {
            this.phy = Objects.requireNonNull(phy, "phy");
            return this;
        }

        @NonNull
        public Builder locationExempt(boolean locationExempt) {
            this.locationExempt = locationExempt;
            return this;
        }

        @NonNull
        public Builder deduplicationWindowMillis(long deduplicationWindowMillis) {
            this.deduplicationWindowMillis = Math.max(0L, deduplicationWindowMillis);
            return this;
        }

        @NonNull
        public Builder matchLostTimeoutMillis(long matchLostTimeoutMillis) {
            this.matchLostTimeoutMillis = Math.max(1_000L, matchLostTimeoutMillis);
            return this;
        }

        @NonNull
        public Builder throttlePolicy(@NonNull BleScanThrottlePolicy throttlePolicy) {
            this.throttlePolicy = Objects.requireNonNull(throttlePolicy, "throttlePolicy");
            return this;
        }

        @NonNull
        public Builder addFilter(@NonNull BleScanFilter filter) {
            this.filters.add(Objects.requireNonNull(filter, "filter"));
            return this;
        }

        @NonNull
        public Builder filters(@NonNull List<BleScanFilter> filters) {
            this.filters.clear();
            this.filters.addAll(Objects.requireNonNull(filters, "filters"));
            return this;
        }

        @NonNull
        public BleScanConfig build() {
            return new BleScanConfig(this);
        }
    }
}
