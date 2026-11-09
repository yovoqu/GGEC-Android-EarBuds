package com.yovo.lib_bluetooth.internal.ble.internal.android.gatt;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.os.Build;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.connection.YoBleConnectionPlatform;
import com.yovo.lib_bluetooth.internal.ble.internal.android.compat.YoBleGattCompat;

/**
 * Android {@link BluetoothGattCallback} 桥接器：
 * <ul>
 *   <li>同时实现 API &lt; 33 与 API 33+ 双签名（{@code byte[]} 参数 overload），并按 SDK 版本隔离绝不双倍回调；</li>
 *   <li>支持 API 26+ PHY/ConnectionUpdated 与 API 31+ {@code onServiceChanged}。</li>
 * </ul>
 */
public final class YoBleAndroidGattCallbackBridge extends BluetoothGattCallback {

    private final YoBleConnectionPlatform.PlatformListener listener;

    public YoBleAndroidGattCallbackBridge(@NonNull YoBleConnectionPlatform.PlatformListener listener) {
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
        if (characteristic == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        byte[] val = characteristic.getValue();
        listener.onCharacteristicRead(
                YoBleGattCompat.targetForCharacteristic(characteristic),
                val == null ? new byte[0] : val.clone(),
                status);
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
                                     @NonNull BluetoothGattCharacteristic characteristic,
                                     @NonNull byte[] value,
                                     int status) {
        listener.onCharacteristicRead(
                YoBleGattCompat.targetForCharacteristic(characteristic),
                value.clone(),
                status);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic,
                                      int status) {
        if (characteristic == null) {
            return;
        }
        byte[] echoed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            echoed = null;
        } else {
            byte[] legacyVal = characteristic.getValue();
            echoed = legacyVal == null ? null : legacyVal.clone();
        }
        listener.onCharacteristicWrite(
                YoBleGattCompat.targetForCharacteristic(characteristic),
                echoed,
                status);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        byte[] val = characteristic.getValue();
        if (val != null) {
            listener.onCharacteristicChanged(
                    YoBleGattCompat.targetForCharacteristic(characteristic),
                    val.clone());
        }
    }

    @Override
    public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                        @NonNull BluetoothGattCharacteristic characteristic,
                                        @NonNull byte[] value) {
        listener.onCharacteristicChanged(
                YoBleGattCompat.targetForCharacteristic(characteristic),
                value.clone());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDescriptorRead(BluetoothGatt gatt,
                                 BluetoothGattDescriptor descriptor,
                                 int status) {
        if (descriptor == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        byte[] val = descriptor.getValue();
        listener.onDescriptorRead(
                YoBleGattCompat.targetForDescriptor(descriptor),
                val == null ? new byte[0] : val.clone(),
                status);
    }

    @Override
    public void onDescriptorRead(@NonNull BluetoothGatt gatt,
                                 @NonNull BluetoothGattDescriptor descriptor,
                                 int status,
                                 @NonNull byte[] value) {
        listener.onDescriptorRead(
                YoBleGattCompat.targetForDescriptor(descriptor),
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
                YoBleGattCompat.targetForDescriptor(descriptor),
                status);
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        listener.onMtuChanged(mtu, status);
    }

    @Override
    public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        listener.onPhyUpdate(YoBleGattCompat.fromAndroidPhyValues(txPhy, rxPhy), status);
    }

    @Override
    public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        listener.onPhyRead(YoBleGattCompat.fromAndroidPhyValues(txPhy, rxPhy), status);
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
}
