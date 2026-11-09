package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

/**
 * 单连接底层 GATT 原语端口（由 Android BluetoothGatt 适配器或 Fake 连接替身实现）。
 */
public interface YoBleGattTransport {

    boolean discoverServices();

    boolean refreshDeviceCache();

    boolean readCharacteristic(@NonNull BleGattTarget target);

    boolean writeCharacteristic(@NonNull BleGattTarget target,
                                @NonNull byte[] value,
                                @NonNull BleWriteType writeType);

    boolean readDescriptor(@NonNull BleGattTarget target);

    boolean writeDescriptor(@NonNull BleGattTarget target, @NonNull byte[] value);

    boolean setCharacteristicNotification(@NonNull BleGattTarget target, boolean enabled);

    boolean requestMtu(int mtu);

    boolean requestConnectionPriority(@NonNull BleConnectionPriority priority);

    boolean setPreferredPhy(@NonNull BlePhy txPhy,
                            @NonNull BlePhy rxPhy,
                            @NonNull BlePhyOptions options);

    boolean readPhy();

    boolean readRemoteRssi();

    boolean beginReliableWrite();

    boolean executeReliableWrite();

    void abortReliableWrite();

    /** 从内部服务树缓存重建不可变快照。 */
    @NonNull
    BleGattDatabaseSnapshot captureDatabaseSnapshot(long timestampMillis);
}
