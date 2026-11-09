package com.yovo.lib_bluetooth.internal.ble.internal.android.compat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanRecordParser;
import com.yovo.lib_bluetooth.model.ble.BleAddressType;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleDeviceType;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            if (f.deviceAddress() != null) {
                b.setDeviceAddress(f.deviceAddress());
            }
            // Note: Android native ScanFilter only does exact match on deviceName,
            // so when deviceNameContains is set we handle matching in YoBleScanFilterMatcher.
            if (f.deviceName() != null && f.deviceNameContains() == null) {
                b.setDeviceName(f.deviceName());
            }
            if (f.serviceUuid() != null) {
                if (f.serviceUuidMask() != null) {
                    b.setServiceUuid(new ParcelUuid(f.serviceUuid()),
                            new ParcelUuid(f.serviceUuidMask()));
                } else {
                    b.setServiceUuid(new ParcelUuid(f.serviceUuid()));
                }
            }
            if (f.solicitationUuid() != null
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (f.solicitationUuidMask() != null) {
                    b.setServiceSolicitationUuid(
                            new ParcelUuid(f.solicitationUuid()),
                            new ParcelUuid(f.solicitationUuidMask()));
                } else {
                    b.setServiceSolicitationUuid(new ParcelUuid(f.solicitationUuid()));
                }
            }
            if (f.serviceDataUuid() != null && f.serviceData() != null) {
                if (f.serviceDataMask() != null
                        && f.serviceDataMask().length == f.serviceData().length) {
                    b.setServiceData(new ParcelUuid(f.serviceDataUuid()),
                            f.serviceData(), f.serviceDataMask());
                } else if (f.serviceDataMask() == null) {
                    b.setServiceData(new ParcelUuid(f.serviceDataUuid()), f.serviceData());
                }
            }
            if (f.manufacturerId() != null && f.manufacturerData() != null) {
                if (f.manufacturerDataMask() != null
                        && f.manufacturerDataMask().length == f.manufacturerData().length) {
                    b.setManufacturerData(f.manufacturerId(),
                            f.manufacturerData(), f.manufacturerDataMask());
                } else if (f.manufacturerDataMask() == null) {
                    b.setManufacturerData(f.manufacturerId(), f.manufacturerData());
                }
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

        String deviceName = "";
        if (dev != null) {
            try {
                String n = dev.getName();
                if (n != null) {
                    deviceName = n;
                }
            } catch (SecurityException ignored) {
            }
        }
        if (deviceName.isEmpty() && parsedRecord.deviceName() != null) {
            deviceName = parsedRecord.deviceName();
        }

        String alias = null;
        if (dev != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
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
