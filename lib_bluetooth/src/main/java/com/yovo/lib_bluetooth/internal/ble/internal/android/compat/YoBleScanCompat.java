package com.yovo.lib_bluetooth.internal.ble.internal.android.compat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.scan.BleScanConfig;
import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanRecordParser;
import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;
import com.yovo.lib_bluetooth.model.ble.BleAddressType;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleDeviceType;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanFilter;
import com.yovo.lib_bluetooth.model.ble.BleScanMatchMode;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Android API 24–37 BLE 扫描设置、Filter 下推与 ScanResult 转换兼容层。
 */
@SuppressLint("MissingPermission")
public final class YoBleScanCompat {

    private YoBleScanCompat() {
    }

    @NonNull
    public static ScanSettings toAndroidScanSettings(@NonNull BleScanConfig config) {
        ScanSettings.Builder b = new ScanSettings.Builder()
                .setScanMode(toAndroidScanMode(config.scanMode()))
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(toAndroidMatchMode(config.matchMode()))
                .setNumOfMatches(config.numOfMatches())
                .setReportDelay(config.reportDelayMillis());

        if (AndroidPlatform.supportsLePhy()) {
            b.setLegacy(config.legacyOnly());
            if (config.phy() == BlePhy.ALL_SUPPORTED) {
                b.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED);
            } else if (config.phy() == BlePhy.LE_CODED) {
                b.setPhy(BluetoothDevice.PHY_LE_CODED);
            } else {
                b.setPhy(BluetoothDevice.PHY_LE_1M);
            }
        }
        return b.build();
    }

    @NonNull
    public static ScanSettings toFallbackScanSettings(@NonNull BleScanConfig config) {
        return new ScanSettings.Builder()
                .setScanMode(toAndroidScanMode(config.scanMode()))
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0L)
                .build();
    }

    @NonNull
    public static List<ScanFilter> toAndroidScanFilters(@NonNull List<BleScanFilter> filters) {
        List<ScanFilter> out = new ArrayList<>(filters.size());
        for (BleScanFilter f : filters) {
            if (f.isEmpty()) {
                continue;
            }
            ScanFilter.Builder b = new ScanFilter.Builder();
            // 关键：只有至少一个字段真正下推到原生 ScanFilter 时才构建。纯软件过滤（minRssi /
            // deviceNameContains / requireConnectable）不产生任何原生字段，若仍 build() 会得到
            // 一个“空 ScanFilter”，在 AOSP 中匹配所有设备并废掉同组其它原生过滤。
            boolean anyNativeCriterion = false;
            if (f.deviceAddress() != null) {
                b.setDeviceAddress(f.deviceAddress());
                anyNativeCriterion = true;
            }
            // Note: Android native ScanFilter only does exact match on deviceName,
            // so when deviceNameContains is set we handle matching in YoBleScanFilterMatcher.
            if (f.deviceName() != null && f.deviceNameContains() == null) {
                b.setDeviceName(f.deviceName());
                anyNativeCriterion = true;
            }
            if (f.serviceUuid() != null) {
                if (f.serviceUuidMask() != null) {
                    b.setServiceUuid(new ParcelUuid(f.serviceUuid()),
                            new ParcelUuid(f.serviceUuidMask()));
                } else {
                    b.setServiceUuid(new ParcelUuid(f.serviceUuid()));
                }
                anyNativeCriterion = true;
            }
            if (f.solicitationUuid() != null
                    && AndroidPlatform.supportsScanSolicitationUuid()) {
                if (f.solicitationUuidMask() != null) {
                    b.setServiceSolicitationUuid(
                            new ParcelUuid(f.solicitationUuid()),
                            new ParcelUuid(f.solicitationUuidMask()));
                } else {
                    b.setServiceSolicitationUuid(new ParcelUuid(f.solicitationUuid()));
                }
                anyNativeCriterion = true;
            }
            if (f.serviceDataUuid() != null && f.serviceData() != null) {
                byte[] serviceData = f.serviceData();
                byte[] serviceDataMask = f.serviceDataMask();
                if (serviceDataMask != null && serviceDataMask.length == serviceData.length) {
                    b.setServiceData(new ParcelUuid(f.serviceDataUuid()), serviceData, serviceDataMask);
                } else if (serviceDataMask == null) {
                    b.setServiceData(new ParcelUuid(f.serviceDataUuid()), serviceData);
                }
                anyNativeCriterion = true;
            }
            if (f.manufacturerId() != null && f.manufacturerData() != null) {
                byte[] manufacturerData = f.manufacturerData();
                byte[] manufacturerDataMask = f.manufacturerDataMask();
                if (manufacturerDataMask != null && manufacturerDataMask.length == manufacturerData.length) {
                    b.setManufacturerData(f.manufacturerId(), manufacturerData, manufacturerDataMask);
                } else if (manufacturerDataMask == null) {
                    b.setManufacturerData(f.manufacturerId(), manufacturerData);
                }
                anyNativeCriterion = true;
            }
            if (!anyNativeCriterion) {
                // 纯软件过滤：交由 YoBleScanFilterMatcher 在软件层匹配，绝不下推空过滤器。
                continue;
            }
            out.add(b.build());
        }
        return out;
    }

    @NonNull
    public static BleScanResult toBleScanResult(@NonNull ScanResult raw,
                                                int callbackType,
                                                long observedAtMillis) {
        BluetoothDevice dev = raw.getDevice();
        String address = dev != null && dev.getAddress() != null ? dev.getAddress() : "00:00:00:00:00:00";
        BleDeviceId deviceId = new BleDeviceId(address);

        ScanRecord rawRecord = raw.getScanRecord();
        byte[] rawBytes = rawRecord != null ? rawRecord.getBytes() : null;
        BleScanRecord parsedRecord = YoBleScanRecordParser.parse(rawBytes);

        // 只取广播 AD Local Name。BluetoothDevice.getName() 是经典/缓存 GAP 名，不能写进 BLE 身份。
        String deviceName = "";
        String nameFromAd = parsedRecord.deviceName();
        if (nameFromAd != null && !nameFromAd.isEmpty()) {
            deviceName = nameFromAd;
        }

        String alias = null;
        if (dev != null && AndroidPlatform.supportsDeviceAlias()) {
            try {
                alias = dev.getAlias();
            } catch (Throwable ignored) {
            }
        }

        BleAddressType addrType = resolveAddressType(dev);
        BleDeviceType devType = resolveDeviceType(dev);

        BleDeviceIdentity identity = new BleDeviceIdentity(
                deviceId, deviceName, alias, addrType, devType, observedAtMillis);

        boolean connectable = true;
        boolean legacy = true;
        BlePhy primaryPhy = BlePhy.LE_1M;
        BlePhy secondaryPhy = BlePhy.UNKNOWN;
        int sid = BleScanResult.SID_NOT_PRESENT;
        int txPower = BleScanResult.TX_POWER_NOT_PRESENT;
        int periodicInterval = BleScanResult.PERIODIC_INTERVAL_NOT_PRESENT;
        if (AndroidPlatform.supportsLePhy()) {
            connectable = raw.isConnectable();
            legacy = raw.isLegacy();
            primaryPhy = YoBleGattCompat.fromAndroidPhy(raw.getPrimaryPhy());
            secondaryPhy = raw.getSecondaryPhy() != ScanResult.PHY_UNUSED
                    ? YoBleGattCompat.fromAndroidPhy(raw.getSecondaryPhy())
                    : BlePhy.UNKNOWN;
            sid = raw.getAdvertisingSid();
            txPower = raw.getTxPower();
            periodicInterval = raw.getPeriodicAdvertisingInterval();
        }

        return new BleScanResult(
                identity,
                raw.getRssi(),
                parsedRecord,
                observedAtMillis,
                connectable,
                legacy,
                primaryPhy,
                secondaryPhy,
                sid,
                txPower,
                periodicInterval,
                fromAndroidCallbackType(callbackType));
    }

    @NonNull
    public static BleAddressType resolveAddressType(BluetoothDevice dev) {
        if (dev == null) {
            return BleAddressType.UNKNOWN;
        }
        if (AndroidPlatform.supportsDeviceAddressType()) {
            try {
                switch (dev.getAddressType()) {
                    case BluetoothDevice.ADDRESS_TYPE_PUBLIC:
                        return BleAddressType.PUBLIC;
                    case BluetoothDevice.ADDRESS_TYPE_RANDOM:
                        return BleAddressType.RANDOM_STATIC;
                    default:
                        return BleAddressType.UNKNOWN;
                }
            } catch (Throwable ignored) {
            }
        }
        return BleAddressType.UNKNOWN;
    }

    @NonNull
    public static BleDeviceType resolveDeviceType(BluetoothDevice dev) {
        if (dev == null) {
            return BleDeviceType.UNKNOWN;
        }
        try {
            switch (dev.getType()) {
                case BluetoothDevice.DEVICE_TYPE_LE:
                    return BleDeviceType.LE;
                case BluetoothDevice.DEVICE_TYPE_DUAL:
                    return BleDeviceType.DUAL;
                case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                    return BleDeviceType.CLASSIC;
                default:
                    return BleDeviceType.UNKNOWN;
            }
        } catch (Throwable ignored) {
            return BleDeviceType.UNKNOWN;
        }
    }

    private static int toAndroidScanMode(@NonNull BleScanMode mode) {
        switch (mode) {
            case OPPORTUNISTIC:
                return ScanSettings.SCAN_MODE_OPPORTUNISTIC;
            case LOW_POWER:
                return ScanSettings.SCAN_MODE_LOW_POWER;
            case LOW_LATENCY:
                return ScanSettings.SCAN_MODE_LOW_LATENCY;
            case BALANCED:
            default:
                return ScanSettings.SCAN_MODE_BALANCED;
        }
    }

    @NonNull
    private static BleScanCallbackType fromAndroidCallbackType(int type) {
        if (type == ScanSettings.CALLBACK_TYPE_FIRST_MATCH) {
            return BleScanCallbackType.FIRST_MATCH;
        }
        if (type == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
            return BleScanCallbackType.MATCH_LOST;
        }
        return BleScanCallbackType.ALL_MATCHES;
    }

    private static int toAndroidMatchMode(@NonNull BleScanMatchMode mode) {
        return mode == BleScanMatchMode.STICKY
                ? ScanSettings.MATCH_MODE_STICKY
                : ScanSettings.MATCH_MODE_AGGRESSIVE;
    }
}
