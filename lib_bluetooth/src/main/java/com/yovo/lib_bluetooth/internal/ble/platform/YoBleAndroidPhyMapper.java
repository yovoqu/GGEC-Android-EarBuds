package com.yovo.lib_bluetooth.internal.ble.platform;

import android.bluetooth.BluetoothDevice;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;

/**
 * BLE PHY 语义（{@link BlePhy} / {@link BlePhyOptions}）与 Android {@code BluetoothDevice} PHY
 * 常量之间的唯一转换点。
 *
 * <p>PHY 相关常量与 {@code ScanResult} / {@code BluetoothGatt} 的 PHY 字段自 API 26 (O) 起存在，
 * 版本判定统一走 {@link AndroidPlatform#supportsLePhy()} / {@link AndroidPlatform#supportsGattPhyControl()}，
 * 本类只做纯值映射，不含版本分支。</p>
 */
public final class YoBleAndroidPhyMapper {

    private YoBleAndroidPhyMapper() {
    }

    /**
     * {@link BlePhy} → PHY 掩码（供 {@code BluetoothGatt#setPreferredPhy}）。
     *
     * <p>{@link BlePhy#ALL_SUPPORTED} 与 {@link BlePhy#UNKNOWN} 都不是具体 PHY，映射为
     * 三种 PHY 掩码的并集，语义是“不锁定单一 PHY”；具体生效结果由 {@code onPhyUpdate} 回报。</p>
     */
    @RequiresApi(Build.VERSION_CODES.O)
    public static int toPhyMask(@NonNull BlePhy phy) {
        switch (phy) {
            case LE_2M:
                return BluetoothDevice.PHY_LE_2M_MASK;
            case LE_CODED:
                return BluetoothDevice.PHY_LE_CODED_MASK;
            case LE_1M:
                return BluetoothDevice.PHY_LE_1M_MASK;
            case ALL_SUPPORTED:
            case UNKNOWN:
                return BluetoothDevice.PHY_LE_1M_MASK
                        | BluetoothDevice.PHY_LE_2M_MASK
                        | BluetoothDevice.PHY_LE_CODED_MASK;
            default:
                throw new IllegalStateException("unhandled BlePhy: " + phy);
        }
    }

    /**
     * Android PHY 取值 → {@link BlePhy}。
     *
     * <p>{@code PHY_LE_1M}、{@code PHY_UNUSED} 与任何未定义取值都归 {@link BlePhy#LE_1M}：
     * BLE 链路的基线 PHY 恒为 1M，只有 2M / Coded 是 API 26 起的附加模式。</p>
     */
    @NonNull
    public static BlePhy fromAndroidPhy(int phy) {
        if (!AndroidPlatform.supportsLePhy()) {
            return BlePhy.LE_1M;
        }
        if (phy == BluetoothDevice.PHY_LE_2M) {
            return BlePhy.LE_2M;
        }
        if (phy == BluetoothDevice.PHY_LE_CODED) {
            return BlePhy.LE_CODED;
        }
        return BlePhy.LE_1M;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public static int toPhyOptions(@NonNull BlePhyOptions options) {
        switch (options) {
            case S2:
                return BluetoothDevice.PHY_OPTION_S2;
            case S8:
                return BluetoothDevice.PHY_OPTION_S8;
            case NO_PREFERRED:
                return BluetoothDevice.PHY_OPTION_NO_PREFERRED;
            default:
                throw new IllegalStateException("unhandled BlePhyOptions: " + options);
        }
    }

    @NonNull
    public static BlePhyConfig fromPhyValues(int txPhy, int rxPhy) {
        return new BlePhyConfig(
                fromAndroidPhy(txPhy),
                fromAndroidPhy(rxPhy),
                BlePhyOptions.NO_PREFERRED);
    }
}
