package com.yovo.lib_bluetooth.internal.ble.internal.android.compat;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSetParameters;
import android.os.Build;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseData;
import com.yovo.lib_bluetooth.model.ble.BlePhy;

import java.util.Map;
import java.util.UUID;

/**
 * BLE 广播设置与载荷转换兼容层（支持 API 24+ Legacy 与 API 26+ Extended AdvertisingSet）。
 */
public final class YoBleAdvertiseCompat {

    private YoBleAdvertiseCompat() {
    }

    @NonNull
    public static AdvertiseSettings toLegacySettings(@NonNull BleAdvertiseConfig config) {
        return new AdvertiseSettings.Builder()
                .setAdvertiseMode(toLegacyMode(config.mode()))
                .setTxPowerLevel(toLegacyTxPower(config.txPowerLevel()))
                .setConnectable(config.connectable())
                .setTimeout(0)
                .build();
    }

    @NonNull
    public static AdvertiseData toAdvertiseData(@NonNull BleAdvertiseData data) {
        AdvertiseData.Builder b = new AdvertiseData.Builder()
                .setIncludeDeviceName(data.includeDeviceName())
                .setIncludeTxPowerLevel(data.includeTxPowerLevel());
        for (UUID svc : data.serviceUuids()) {
            b.addServiceUuid(new ParcelUuid(svc));
        }
        if (AndroidPlatform.supportsAdvertisingSolicitationUuid()) {
            for (UUID sol : data.serviceSolicitationUuids()) {
                b.addServiceSolicitationUuid(new ParcelUuid(sol));
            }
        }
        for (Map.Entry<UUID, byte[]> entry : data.serviceData().entrySet()) {
            b.addServiceData(new ParcelUuid(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<Integer, byte[]> entry : data.manufacturerData().entrySet()) {
            b.addManufacturerData(entry.getKey(), entry.getValue());
        }
        return b.build();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @NonNull
    public static AdvertisingSetParameters toExtendedParameters(@NonNull BleAdvertiseConfig config) {
        boolean scannable = config.legacyMode()
                ? (config.connectable() || config.scannable())
                : config.scannable();
        AdvertisingSetParameters.Builder b = new AdvertisingSetParameters.Builder()
                .setLegacyMode(config.legacyMode())
                .setConnectable(config.connectable())
                .setScannable(scannable)
                .setInterval(Math.max(160, Math.min(16_777_215, config.intervalUnits())))
                .setTxPowerLevel(toExtendedTxPower(config.txPowerLevel()));
        if (!config.legacyMode()) {
            b.setPrimaryPhy(toAdvertisingPhy(config.primaryPhy(), false))
                    .setSecondaryPhy(toAdvertisingPhy(config.secondaryPhy(), true));
        }
        return b.build();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static int toAdvertisingPhy(@NonNull BlePhy phy, boolean allow2M) {
        if (phy == BlePhy.LE_CODED) {
            return BluetoothDevice.PHY_LE_CODED;
        }
        if (allow2M && phy == BlePhy.LE_2M) {
            return BluetoothDevice.PHY_LE_2M;
        }
        return BluetoothDevice.PHY_LE_1M;
    }

    private static int toLegacyMode(@NonNull BleAdvertiseConfig.AdvertiseMode mode) {
        switch (mode) {
            case LOW_POWER:
                return AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;
            case LOW_LATENCY:
                return AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY;
            case BALANCED:
            default:
                return AdvertiseSettings.ADVERTISE_MODE_BALANCED;
        }
    }

    private static int toLegacyTxPower(@NonNull BleAdvertiseConfig.TxPowerLevel level) {
        switch (level) {
            case ULTRA_LOW:
                return AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW;
            case LOW:
                return AdvertiseSettings.ADVERTISE_TX_POWER_LOW;
            case HIGH:
                return AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;
            case MEDIUM:
            default:
                return AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static int toExtendedTxPower(@NonNull BleAdvertiseConfig.TxPowerLevel level) {
        switch (level) {
            case ULTRA_LOW:
                return AdvertisingSetParameters.TX_POWER_ULTRA_LOW;
            case LOW:
                return AdvertisingSetParameters.TX_POWER_LOW;
            case HIGH:
                return AdvertisingSetParameters.TX_POWER_HIGH;
            case MEDIUM:
            default:
                return AdvertisingSetParameters.TX_POWER_MEDIUM;
        }
    }
}
