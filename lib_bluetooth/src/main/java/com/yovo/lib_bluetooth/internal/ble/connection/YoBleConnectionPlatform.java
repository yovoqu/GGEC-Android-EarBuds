package com.yovo.lib_bluetooth.internal.ble.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattTransport;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;

/**
 * 底层 BLE GATT 连接创建与事件回调端口（由 Android BluetoothGatt 适配器或 Fake 平台实现）。
 */
public interface YoBleConnectionPlatform {

    int STATE_DISCONNECTED = 0;
    int STATE_CONNECTED = 2;

    interface BleGattHandle {
        @NonNull
        YoBleGattTransport transport();

        void disconnect();

        void close();

        boolean isClosed();
    }

    interface PlatformListener {
        void onConnectionStateChange(int status, int newState);

        void onServicesDiscovered(int status);

        void onServiceChanged();

        void onCharacteristicRead(@NonNull BleGattTarget target, @NonNull byte[] value, int status);

        void onCharacteristicWrite(@NonNull BleGattTarget target, @Nullable byte[] echoedValue, int status);

        void onCharacteristicChanged(@NonNull BleGattTarget target, @NonNull byte[] value);

        void onDescriptorRead(@NonNull BleGattTarget target, @NonNull byte[] value, int status);

        void onDescriptorWrite(@NonNull BleGattTarget target, int status);

        void onMtuChanged(int mtu, int status);

        void onPhyUpdate(@NonNull BlePhyConfig phy, int status);

        void onPhyRead(@NonNull BlePhyConfig phy, int status);

        void onConnectionUpdated(int interval, int latency, int timeout, int status);

        void onReadRemoteRssi(int rssi, int status);

        void onReliableWriteCompleted(int status);
    }

    @Nullable
    BleGattHandle connectGatt(@NonNull BleDeviceId deviceId,
                              boolean autoConnect,
                              @NonNull PlatformListener listener);
}
