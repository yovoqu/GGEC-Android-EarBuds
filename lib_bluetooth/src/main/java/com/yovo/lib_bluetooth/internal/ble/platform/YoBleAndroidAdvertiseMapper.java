package com.yovo.lib_bluetooth.internal.ble.platform;

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
 * {@link BleAdvertiseConfig} / {@link BleAdvertiseData} → Android 广播设置的唯一转换点。
 *
 * <p>同一份载荷同时服务两条平台通路：Legacy {@code BluetoothLeAdvertiser#startAdvertising}
 * 与 API 26 起的 {@code startAdvertisingSet}。通路选择由调用方的
 * {@link AndroidPlatform#supportsExtendedAdvertising()} 语义决定，本类只做值转换。</p>
 */
public final class YoBleAndroidAdvertiseMapper {

    private YoBleAndroidAdvertiseMapper() {
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
        AdvertiseData.Builder builder = new AdvertiseData.Builder()
                .setIncludeDeviceName(data.includeDeviceName())
                .setIncludeTxPowerLevel(data.includeTxPowerLevel());
        for (UUID serviceUuid : data.serviceUuids()) {
            builder.addServiceUuid(new ParcelUuid(serviceUuid));
        }
        if (AndroidPlatform.supportsAdvertisingSolicitationUuid()) {
            for (UUID solicitation : data.serviceSolicitationUuids()) {
                builder.addServiceSolicitationUuid(new ParcelUuid(solicitation));
            }
        }
        for (Map.Entry<UUID, byte[]> entry : data.serviceData().entrySet()) {
            builder.addServiceData(new ParcelUuid(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<Integer, byte[]> entry : data.manufacturerData().entrySet()) {
            builder.addManufacturerData(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @NonNull
    public static AdvertisingSetParameters toExtendedParameters(@NonNull BleAdvertiseConfig config) {
        // Legacy 广播可被连接即可被扫描；非 Legacy 时可扫描性单独由配置决定。
        boolean scannable = config.legacyMode()
                ? (config.connectable() || config.scannable())
                : config.scannable();
        AdvertisingSetParameters.Builder builder = new AdvertisingSetParameters.Builder()
                .setLegacyMode(config.legacyMode())
                .setConnectable(config.connectable())
                .setScannable(scannable)
                .setInterval(Math.max(160, Math.min(16_777_215, config.intervalUnits())))
                .setTxPowerLevel(toExtendedTxPower(config.txPowerLevel()));
        if (!config.legacyMode()) {
            builder.setPrimaryPhy(toAdvertisingPhy(config.primaryPhy(), false))
                    .setSecondaryPhy(toAdvertisingPhy(config.secondaryPhy(), true));
        }
        return builder.build();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static int toAdvertisingPhy(@NonNull BlePhy phy, boolean allow2M) {
        if (phy == BlePhy.LE_CODED) {
            return BluetoothDevice.PHY_LE_CODED;
        }
        if (allow2M && phy == BlePhy.LE_2M) {
            return BluetoothDevice.PHY_LE_2M;
        }
        // 广播主 PHY 只支持 1M 与 Coded；2M 只在次 PHY 上可选，其余取值落到 1M。
        return BluetoothDevice.PHY_LE_1M;
    }

    private static int toLegacyMode(@NonNull BleAdvertiseConfig.AdvertiseMode mode) {
        switch (mode) {
            case LOW_POWER:
                return AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;
            case LOW_LATENCY:
                return AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY;
            case BALANCED:
                return AdvertiseSettings.ADVERTISE_MODE_BALANCED;
            default:
                throw new IllegalStateException("unhandled AdvertiseMode: " + mode);
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
                return AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
            default:
                throw new IllegalStateException("unhandled TxPowerLevel: " + level);
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
                return AdvertisingSetParameters.TX_POWER_MEDIUM;
            default:
                throw new IllegalStateException("unhandled TxPowerLevel: " + level);
        }
    }
}
