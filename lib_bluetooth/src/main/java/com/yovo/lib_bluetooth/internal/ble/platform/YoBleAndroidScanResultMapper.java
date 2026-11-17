package com.yovo.lib_bluetooth.internal.ble.platform;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanRecordParser;
import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

/**
 * Android {@link ScanResult} → {@link BleScanResult} 的唯一转换点。
 *
 * <p>会做 AD TLV 解析与多次同步 Binder IPC（getAlias / getType / getAddressType），调用方需保证
 * 不在主线程执行（见 {@code YoBleAndroidScanPlatform} 的 parse 线程）。</p>
 */
public final class YoBleAndroidScanResultMapper {

    /** 平台未给出设备对象/地址时的占位地址：仍是合法的 6 字节 MAC 形式，供 {@link BleDeviceId} 校验。 */
    private static final String ABSENT_ADDRESS = "00:00:00:00:00:00";

    private YoBleAndroidScanResultMapper() {
    }

    @NonNull
    public static BleScanResult toBleScanResult(@NonNull ScanResult raw,
                                                int callbackType,
                                                long observedAtMillis) {
        BluetoothDevice device = raw.getDevice();
        String address = device != null && device.getAddress() != null
                ? device.getAddress()
                : ABSENT_ADDRESS;
        BleDeviceId deviceId = new BleDeviceId(address);

        ScanRecord rawRecord = raw.getScanRecord();
        byte[] rawBytes = rawRecord != null ? rawRecord.getBytes() : null;
        BleScanRecord parsedRecord = YoBleScanRecordParser.parse(rawBytes);

        // 只取广播 AD Local Name。BluetoothDevice.getName() 是经典/缓存 GAP 名，不能写进 BLE 身份。
        BleDeviceIdentity identity = device != null
                ? YoBleAndroidDeviceMapper.identity(
                        device, deviceId, localNameOf(parsedRecord), observedAtMillis)
                : new BleDeviceIdentity(deviceId, localNameOf(parsedRecord), observedAtMillis);

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
            primaryPhy = YoBleAndroidPhyMapper.fromAndroidPhy(raw.getPrimaryPhy());
            secondaryPhy = raw.getSecondaryPhy() != ScanResult.PHY_UNUSED
                    ? YoBleAndroidPhyMapper.fromAndroidPhy(raw.getSecondaryPhy())
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
    private static String localNameOf(@NonNull BleScanRecord record) {
        String name = record.deviceName();
        return name == null ? "" : name;
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
}
