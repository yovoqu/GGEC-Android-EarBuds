package com.yovo.lib_bluetooth.internal.ble.internal.android.gatt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Build;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattTransport;
import com.yovo.lib_bluetooth.internal.ble.internal.android.compat.YoBleGattCompat;
import com.yovo.lib_bluetooth.internal.ble.internal.android.hiddenapi.YoBleGattCacheRefresher;
import com.yovo.lib_bluetooth.internal.ble.internal.android.hiddenapi.YoBleHiddenApiPolicy;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

/**
 * 基于真实 Android {@link BluetoothGatt} 的 {@link YoBleGattTransport} 实现。
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidGattTransport implements YoBleGattTransport {

    private final BleDeviceId deviceId;
    private final BluetoothGatt gatt;
    private final YoBleHiddenApiPolicy cacheRefreshPolicy;

    public YoBleAndroidGattTransport(@NonNull BleDeviceId deviceId,
                                     @NonNull BluetoothGatt gatt,
                                     @NonNull YoBleHiddenApiPolicy cacheRefreshPolicy) {
        this.deviceId = deviceId;
        this.gatt = gatt;
        this.cacheRefreshPolicy = cacheRefreshPolicy;
    }

    @Override
    public boolean discoverServices() {
        return gatt.discoverServices();
    }

    @Override
    public boolean refreshDeviceCache() {
        return YoBleGattCacheRefresher.refresh(gatt, cacheRefreshPolicy);
    }

    @Override
    public boolean readCharacteristic(@NonNull BleGattTarget target) {
        BluetoothGattCharacteristic ch = YoBleGattCompat.findCharacteristic(gatt, target);
        return ch != null && gatt.readCharacteristic(ch);
    }

    @Override
    public boolean writeCharacteristic(@NonNull BleGattTarget target,
                                       @NonNull byte[] value,
                                       @NonNull BleWriteType writeType) {
        BluetoothGattCharacteristic ch = YoBleGattCompat.findCharacteristic(gatt, target);
        return ch != null && YoBleGattCompat.writeCharacteristic(gatt, ch, value, writeType);
    }

    @Override
    public boolean readDescriptor(@NonNull BleGattTarget target) {
        BluetoothGattDescriptor desc = YoBleGattCompat.findDescriptor(gatt, target);
        return desc != null && gatt.readDescriptor(desc);
    }

    @Override
    public boolean writeDescriptor(@NonNull BleGattTarget target, @NonNull byte[] value) {
        BluetoothGattDescriptor desc = YoBleGattCompat.findDescriptor(gatt, target);
        return desc != null && YoBleGattCompat.writeDescriptor(gatt, desc, value);
    }

    @Override
    public boolean setCharacteristicNotification(@NonNull BleGattTarget target, boolean enabled) {
        BluetoothGattCharacteristic ch = YoBleGattCompat.findCharacteristic(gatt, target);
        return ch != null && gatt.setCharacteristicNotification(ch, enabled);
    }

    @Override
    public boolean requestMtu(int mtu) {
        return gatt.requestMtu(mtu);
    }

    @Override
    public boolean requestConnectionPriority(@NonNull BleConnectionPriority priority) {
        return gatt.requestConnectionPriority(YoBleGattCompat.toAndroidConnectionPriority(priority));
    }

    @Override
    public boolean setPreferredPhy(@NonNull BlePhy txPhy,
                                   @NonNull BlePhy rxPhy,
                                   @NonNull BlePhyOptions options) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        gatt.setPreferredPhy(
                YoBleGattCompat.toAndroidPhyMask(txPhy),
                YoBleGattCompat.toAndroidPhyMask(rxPhy),
                YoBleGattCompat.toAndroidPhyOptions(options));
        return true;
    }

    @Override
    public boolean readPhy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        gatt.readPhy();
        return true;
    }

    @Override
    public boolean readRemoteRssi() {
        return gatt.readRemoteRssi();
    }

    @Override
    public boolean beginReliableWrite() {
        return gatt.beginReliableWrite();
    }

    @Override
    public boolean executeReliableWrite() {
        return gatt.executeReliableWrite();
    }

    @Override
    public void abortReliableWrite() {
        gatt.abortReliableWrite();
    }

    @NonNull
    @Override
    public BleGattDatabaseSnapshot captureDatabaseSnapshot(long timestampMillis) {
        return YoBleGattCompat.captureDatabaseSnapshot(deviceId, gatt, timestampMillis);
    }
}
