package com.yovo.lib_bluetooth.internal.ble.platform.gatt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.connection.YoBleConnectionPlatform;
import com.yovo.lib_bluetooth.internal.ble.platform.YoBleAndroidPhyMapper;
import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;

/**
 * Android {@link BluetoothGattCallback} → {@link YoBleConnectionPlatform.PlatformListener} 的适配：
 * 把系统回调的原始 status / 字节数组翻译成连接层语义。
 *
 * <p>读/写/通知回调在 API 33 有两套签名：33 起系统只调带 {@code byte[]} 参数的重载，
 * 之前只调不带值的重载。派发路径按 {@link AndroidPlatform#usesTypedGattValueCallbacks()} 单向选择，
 * 另一条签名在同一版本下直接返回，不会双倍回调。</p>
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidGattCallback extends BluetoothGattCallback {

    private final YoBleConnectionPlatform.PlatformListener listener;

    public YoBleAndroidGattCallback(@NonNull YoBleConnectionPlatform.PlatformListener listener) {
        this.listener = listener;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        int mappedState = newState == BluetoothProfile.STATE_CONNECTED
                ? YoBleConnectionPlatform.STATE_CONNECTED
                : YoBleConnectionPlatform.STATE_DISCONNECTED;
        listener.onConnectionStateChange(status, mappedState);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        listener.onServicesDiscovered(status);
    }

    @Override
    public void onServiceChanged(@NonNull BluetoothGatt gatt) {
        listener.onServiceChanged();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
        if (characteristic == null || AndroidPlatform.usesTypedGattValueCallbacks()) {
            return;
        }
        byte[] value = characteristic.getValue();
        listener.onCharacteristicRead(
                YoBleAndroidGattAddressing.targetForCharacteristic(characteristic),
                value == null ? new byte[0] : value.clone(),
                status);
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
                                     @NonNull BluetoothGattCharacteristic characteristic,
                                     @NonNull byte[] value,
                                     int status) {
        listener.onCharacteristicRead(
                YoBleAndroidGattAddressing.targetForCharacteristic(characteristic),
                value.clone(),
                status);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic,
                                      int status) {
        if (characteristic == null) {
            return;
        }
        byte[] echoedValue = AndroidPlatform.usesTypedGattValueCallbacks()
                ? null
                : characteristicValueSnapshot(characteristic);
        listener.onCharacteristicWrite(
                YoBleAndroidGattAddressing.targetForCharacteristic(characteristic),
                echoedValue,
                status);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || AndroidPlatform.usesTypedGattValueCallbacks()) {
            return;
        }
        byte[] value = characteristic.getValue();
        if (value != null) {
            listener.onCharacteristicChanged(
                    YoBleAndroidGattAddressing.targetForCharacteristic(characteristic),
                    value.clone());
        }
    }

    @Override
    public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                        @NonNull BluetoothGattCharacteristic characteristic,
                                        @NonNull byte[] value) {
        listener.onCharacteristicChanged(
                YoBleAndroidGattAddressing.targetForCharacteristic(characteristic),
                value.clone());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDescriptorRead(BluetoothGatt gatt,
                                 BluetoothGattDescriptor descriptor,
                                 int status) {
        if (descriptor == null || AndroidPlatform.usesTypedGattValueCallbacks()) {
            return;
        }
        byte[] value = descriptor.getValue();
        listener.onDescriptorRead(
                YoBleAndroidGattAddressing.targetForDescriptor(descriptor),
                value == null ? new byte[0] : value.clone(),
                status);
    }

    @Override
    public void onDescriptorRead(@NonNull BluetoothGatt gatt,
                                 @NonNull BluetoothGattDescriptor descriptor,
                                 int status,
                                 @NonNull byte[] value) {
        listener.onDescriptorRead(
                YoBleAndroidGattAddressing.targetForDescriptor(descriptor),
                value.clone(),
                status);
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt,
                                  BluetoothGattDescriptor descriptor,
                                  int status) {
        if (descriptor == null) {
            return;
        }
        listener.onDescriptorWrite(
                YoBleAndroidGattAddressing.targetForDescriptor(descriptor),
                status);
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        listener.onMtuChanged(mtu, status);
    }

    @Override
    public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        listener.onPhyUpdate(YoBleAndroidPhyMapper.fromPhyValues(txPhy, rxPhy), status);
    }

    @Override
    public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        listener.onPhyRead(YoBleAndroidPhyMapper.fromPhyValues(txPhy, rxPhy), status);
    }

    @Override
    public void onConnectionUpdated(BluetoothGatt gatt,
                                    int interval,
                                    int latency,
                                    int timeout,
                                    int status) {
        listener.onConnectionUpdated(interval, latency, timeout, status);
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        listener.onReadRemoteRssi(rssi, status);
    }

    @Override
    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        listener.onReliableWriteCompleted(status);
    }

    /**
     * API 33 之前的写入回调不带值：回调发生时特征上保留的就是本次写入的回显。
     * 值需要复制，系统可能复用同一个特征对象。
     */
    @Nullable
    @SuppressWarnings("deprecation")
    private static byte[] characteristicValueSnapshot(
            @NonNull BluetoothGattCharacteristic characteristic) {
        byte[] value = characteristic.getValue();
        return value == null ? null : value.clone();
    }
}
