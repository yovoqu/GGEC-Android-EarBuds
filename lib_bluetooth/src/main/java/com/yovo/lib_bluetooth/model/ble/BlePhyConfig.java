package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * BLE TX/RX PHY 配置与状态快照。
 */
public final class BlePhyConfig {

    @NonNull
    private final BlePhy txPhy;
    @NonNull
    private final BlePhy rxPhy;
    @NonNull
    private final BlePhyOptions phyOptions;

    public BlePhyConfig(@NonNull BlePhy txPhy,
                        @NonNull BlePhy rxPhy,
                        @NonNull BlePhyOptions phyOptions) {
        this.txPhy = Objects.requireNonNull(txPhy, "txPhy");
        this.rxPhy = Objects.requireNonNull(rxPhy, "rxPhy");
        this.phyOptions = Objects.requireNonNull(phyOptions, "phyOptions");
    }

    @NonNull
    public static BlePhyConfig le1m() {
        return new BlePhyConfig(BlePhy.LE_1M, BlePhy.LE_1M, BlePhyOptions.NO_PREFERRED);
    }

    @NonNull
    public static BlePhyConfig le2m() {
        return new BlePhyConfig(BlePhy.LE_2M, BlePhy.LE_2M, BlePhyOptions.NO_PREFERRED);
    }

    @NonNull
    public static BlePhyConfig leCoded(@NonNull BlePhyOptions options) {
        return new BlePhyConfig(BlePhy.LE_CODED, BlePhy.LE_CODED, options);
    }

    @NonNull
    public BlePhy txPhy() {
        return txPhy;
    }

    @NonNull
    public BlePhy rxPhy() {
        return rxPhy;
    }

    @NonNull
    public BlePhyOptions phyOptions() {
        return phyOptions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlePhyConfig)) return false;
        BlePhyConfig that = (BlePhyConfig) o;
        return txPhy == that.txPhy && rxPhy == that.rxPhy && phyOptions == that.phyOptions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(txPhy, rxPhy, phyOptions);
    }
}
