package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanFilter;
import com.yovo.lib_bluetooth.model.ble.BleScanMatchMode;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;
import com.yovo.lib_bluetooth.model.ble.BleScanThrottlePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * BLE 扫描配置（ScanMode / CallbackType / Batching / MatchMode / PHY / Filters / 限流策略）。
 *
 * <p>库内部扫描策略的唯一载体：不对外暴露，调用方只能通过 {@code YoBleClient.subscribeScan}
 * 订阅，无法构造或注入本配置。</p>
 *
 * <p>配置值全部是代码常量，非法即编程错误：{@link Builder} 在每个字段上按声明的合法域校验并抛
 * {@link IllegalArgumentException}（消息含字段名与非法值），不做任何钳位——钳位会把非法输入变成
 * 「看起来生效」的值，掩盖调用点的错误。</p>
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

    /**
     * 软件降级配置：清空全部原生 ScanFilter 并将 reportDelay 置 0，用于底层 onScanFailed 的
     * {@code SCAN_FAILED_FEATURE_UNSUPPORTED} / {@code SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES}
     * 场景下重试一次（对齐 Nordic Android-Scanner-Compat-Library 的降级重试思路）。
     */
    @NonNull
    public BleScanConfig softwareFallback() {
        Builder builder = new Builder(this);
        builder.filters(Collections.emptyList());
        builder.reportDelayMillis(0L);
        return builder.build();
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        /** 扫描超时默认值唯一来源；产品扫描无超时，由 {@link YoBleScanDefaults} 显式置 0。 */
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
        /** 0 表示不启用软件 lost；具体阈值由 {@link YoBleScanDefaults} 单点给出。 */
        private long matchLostTimeoutMillis = 0L;
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

        /**
         * 扫描超时。
         *
         * <p>合法域：{@code timeoutMillis >= 0}，其中 {@code 0} 表示无超时（产品语义，由
         * {@link YoBleScanDefaults} 显式给出）。</p>
         *
         * @throws IllegalArgumentException 传入负值
         */
        @NonNull
        public Builder timeoutMillis(long timeoutMillis) {
            if (timeoutMillis < 0L) {
                throw new IllegalArgumentException(
                        "timeoutMillis must be >= 0 (0 = no timeout), but was " + timeoutMillis);
            }
            this.timeoutMillis = timeoutMillis;
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

        /**
         * 同一过滤条件连续命中多少次才上报（Android {@code setNumOfMatches}）。
         *
         * <p>合法域：{@code 1..3}（平台只接受 1 / 2 / 3）。</p>
         *
         * @throws IllegalArgumentException 传入 {@code < 1} 或 {@code > 3}
         */
        @NonNull
        public Builder numOfMatches(int numOfMatches) {
            if (numOfMatches < 1 || numOfMatches > 3) {
                throw new IllegalArgumentException(
                        "numOfMatches must be in 1..3, but was " + numOfMatches);
            }
            this.numOfMatches = numOfMatches;
            return this;
        }

        /**
         * 批处理上报延迟（Android {@code setReportDelay}）。
         *
         * <p>合法域：{@code reportDelayMillis >= 0}，其中 {@code 0} 表示不批处理。
         * 正值为软件批处理的启用条件，因此启用方的间隔必然为正。</p>
         *
         * @throws IllegalArgumentException 传入负值
         */
        @NonNull
        public Builder reportDelayMillis(long reportDelayMillis) {
            if (reportDelayMillis < 0L) {
                throw new IllegalArgumentException(
                        "reportDelayMillis must be >= 0, but was " + reportDelayMillis);
            }
            this.reportDelayMillis = reportDelayMillis;
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

        /**
         * 软件去重时间窗：同一设备在该窗口内重复广播只上报一次。
         *
         * <p>合法域：{@code deduplicationWindowMillis >= 0}，其中 {@code 0} 表示不去重。</p>
         *
         * @throws IllegalArgumentException 传入负值
         */
        @NonNull
        public Builder deduplicationWindowMillis(long deduplicationWindowMillis) {
            if (deduplicationWindowMillis < 0L) {
                throw new IllegalArgumentException(
                        "deduplicationWindowMillis must be >= 0, but was " + deduplicationWindowMillis);
            }
            this.deduplicationWindowMillis = deduplicationWindowMillis;
            return this;
        }

        /**
         * 单台设备末次广播后判 lost 的软件阈值。
         *
         * <p>合法域：{@code matchLostTimeoutMillis >= 0}，其中 {@code 0} 表示关闭软件 lost 判定。</p>
         *
         * @throws IllegalArgumentException 传入负值
         */
        @NonNull
        public Builder matchLostTimeoutMillis(long matchLostTimeoutMillis) {
            if (matchLostTimeoutMillis < 0L) {
                throw new IllegalArgumentException(
                        "matchLostTimeoutMillis must be >= 0 (0 = disabled), but was "
                                + matchLostTimeoutMillis);
            }
            this.matchLostTimeoutMillis = matchLostTimeoutMillis;
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
