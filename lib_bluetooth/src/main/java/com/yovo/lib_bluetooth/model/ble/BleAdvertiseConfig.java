package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * BLE 外设广播配置（兼容 Legacy API 24+ 与 Extended AdvertisingSet API 26+）。
 */
public final class BleAdvertiseConfig {

    public enum AdvertiseMode {
        LOW_POWER, BALANCED, LOW_LATENCY
    }

    public enum TxPowerLevel {
        ULTRA_LOW, LOW, MEDIUM, HIGH
    }

    @NonNull
    private final AdvertiseMode mode;
    @NonNull
    private final TxPowerLevel txPowerLevel;
    private final boolean connectable;
    private final boolean scannable;
    private final boolean legacyMode;
    @NonNull
    private final BlePhy primaryPhy;
    @NonNull
    private final BlePhy secondaryPhy;
    private final int intervalUnits;
    private final int timeoutMillis;

    private BleAdvertiseConfig(Builder builder) {
        this.mode = builder.mode;
        this.txPowerLevel = builder.txPowerLevel;
        this.connectable = builder.connectable;
        this.scannable = builder.scannable;
        this.legacyMode = builder.legacyMode;
        this.primaryPhy = builder.primaryPhy;
        this.secondaryPhy = builder.secondaryPhy;
        this.intervalUnits = builder.intervalUnits;
        this.timeoutMillis = builder.timeoutMillis;
    }

    @NonNull
    public AdvertiseMode mode() {
        return mode;
    }

    @NonNull
    public TxPowerLevel txPowerLevel() {
        return txPowerLevel;
    }

    public boolean connectable() {
        return connectable;
    }

    public boolean scannable() {
        return scannable;
    }

    public boolean legacyMode() {
        return legacyMode;
    }

    @NonNull
    public BlePhy primaryPhy() {
        return primaryPhy;
    }

    @NonNull
    public BlePhy secondaryPhy() {
        return secondaryPhy;
    }

    public int intervalUnits() {
        return intervalUnits;
    }

    public int timeoutMillis() {
        return timeoutMillis;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AdvertiseMode mode = AdvertiseMode.BALANCED;
        private TxPowerLevel txPowerLevel = TxPowerLevel.MEDIUM;
        private boolean connectable = true;
        private boolean scannable = true;
        private boolean legacyMode = true;
        private BlePhy primaryPhy = BlePhy.LE_1M;
        private BlePhy secondaryPhy = BlePhy.LE_1M;
        private int intervalUnits = 160;
        private int timeoutMillis = 0;

        @NonNull
        public Builder mode(@NonNull AdvertiseMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        @NonNull
        public Builder txPowerLevel(@NonNull TxPowerLevel txPowerLevel) {
            this.txPowerLevel = Objects.requireNonNull(txPowerLevel, "txPowerLevel");
            return this;
        }

        @NonNull
        public Builder connectable(boolean connectable) {
            this.connectable = connectable;
            return this;
        }

        @NonNull
        public Builder scannable(boolean scannable) {
            this.scannable = scannable;
            return this;
        }

        @NonNull
        public Builder legacyMode(boolean legacyMode) {
            this.legacyMode = legacyMode;
            return this;
        }

        @NonNull
        public Builder primaryPhy(@NonNull BlePhy primaryPhy) {
            this.primaryPhy = Objects.requireNonNull(primaryPhy, "primaryPhy");
            return this;
        }

        @NonNull
        public Builder secondaryPhy(@NonNull BlePhy secondaryPhy) {
            this.secondaryPhy = Objects.requireNonNull(secondaryPhy, "secondaryPhy");
            return this;
        }

        @NonNull
        public Builder intervalUnits(int intervalUnits) {
            this.intervalUnits = Math.max(32, Math.min(16777215, intervalUnits));
            return this;
        }

        @NonNull
        public Builder timeoutMillis(int timeoutMillis) {
            this.timeoutMillis = Math.max(0, timeoutMillis);
            return this;
        }

        @NonNull
        public BleAdvertiseConfig build() {
            return new BleAdvertiseConfig(this);
        }
    }
}
