package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * BLE 原子化“触发写入 + 等待 Notify/Indicate 匹配响应”请求配置（消除先写后听竞态）。
 */
public final class BleRequestResponseConfig {

    @NonNull
    private final BleGattTarget requestTarget;
    @NonNull
    private final byte[] requestPayload;
    @NonNull
    private final BleWriteType writeType;
    @NonNull
    private final BleGattTarget responseTarget;
    @NonNull
    private final BleNotificationSetupMode setupMode;
    @Nullable
    private final Predicate<byte[]> responseMatcher;
    @Nullable
    private final BleDataMerger dataMerger;
    private final long timeoutMillis;
    @NonNull
    private final BleOperationPriority priority;

    private BleRequestResponseConfig(Builder builder) {
        this.requestTarget = Objects.requireNonNull(builder.requestTarget, "requestTarget");
        this.requestPayload = Objects.requireNonNull(builder.requestPayload, "requestPayload").clone();
        this.writeType = builder.writeType;
        this.responseTarget = Objects.requireNonNull(builder.responseTarget, "responseTarget");
        this.setupMode = builder.setupMode;
        this.responseMatcher = builder.responseMatcher;
        this.dataMerger = builder.dataMerger;
        this.timeoutMillis = builder.timeoutMillis;
        this.priority = builder.priority;
    }

    @NonNull
    public BleGattTarget requestTarget() {
        return requestTarget;
    }

    @NonNull
    public byte[] requestPayload() {
        return requestPayload.clone();
    }

    @NonNull
    public BleWriteType writeType() {
        return writeType;
    }

    @NonNull
    public BleGattTarget responseTarget() {
        return responseTarget;
    }

    @NonNull
    public BleNotificationSetupMode setupMode() {
        return setupMode;
    }

    @Nullable
    public Predicate<byte[]> responseMatcher() {
        return responseMatcher;
    }

    @Nullable
    public BleDataMerger dataMerger() {
        return dataMerger;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    @NonNull
    public BleOperationPriority priority() {
        return priority;
    }

    @NonNull
    public static Builder builder(@NonNull BleGattTarget requestTarget,
                                  @NonNull byte[] requestPayload,
                                  @NonNull BleGattTarget responseTarget) {
        return new Builder(requestTarget, requestPayload, responseTarget);
    }

    public static final class Builder {
        @NonNull
        private final BleGattTarget requestTarget;
        @NonNull
        private final byte[] requestPayload;
        @NonNull
        private final BleGattTarget responseTarget;
        private BleWriteType writeType = BleWriteType.WITH_RESPONSE;
        private BleNotificationSetupMode setupMode = BleNotificationSetupMode.COMPAT;
        @Nullable
        private Predicate<byte[]> responseMatcher;
        @Nullable
        private BleDataMerger dataMerger;
        private long timeoutMillis = 5_000L;
        private BleOperationPriority priority = BleOperationPriority.NORMAL;

        private Builder(@NonNull BleGattTarget requestTarget,
                        @NonNull byte[] requestPayload,
                        @NonNull BleGattTarget responseTarget) {
            this.requestTarget = requestTarget;
            this.requestPayload = requestPayload;
            this.responseTarget = responseTarget;
        }

        @NonNull
        public Builder writeType(@NonNull BleWriteType writeType) {
            this.writeType = Objects.requireNonNull(writeType, "writeType");
            return this;
        }

        @NonNull
        public Builder setupMode(@NonNull BleNotificationSetupMode setupMode) {
            this.setupMode = Objects.requireNonNull(setupMode, "setupMode");
            return this;
        }

        @NonNull
        public Builder responseMatcher(@Nullable Predicate<byte[]> responseMatcher) {
            this.responseMatcher = responseMatcher;
            return this;
        }

        @NonNull
        public Builder dataMerger(@Nullable BleDataMerger dataMerger) {
            this.dataMerger = dataMerger;
            return this;
        }

        @NonNull
        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = Math.max(200L, timeoutMillis);
            return this;
        }

        @NonNull
        public Builder priority(@NonNull BleOperationPriority priority) {
            this.priority = Objects.requireNonNull(priority, "priority");
            return this;
        }

        @NonNull
        public BleRequestResponseConfig build() {
            return new BleRequestResponseConfig(this);
        }
    }
}
