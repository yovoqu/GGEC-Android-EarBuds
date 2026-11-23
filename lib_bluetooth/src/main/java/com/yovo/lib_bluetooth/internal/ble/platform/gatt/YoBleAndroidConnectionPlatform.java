package com.yovo.lib_bluetooth.internal.ble.platform.gatt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.connection.YoBleConnectionPlatform;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattTransport;
import com.yovo.lib_bluetooth.internal.ble.platform.hiddenapi.YoBleHiddenApiPolicy;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.bluetooth.radio.YoBtRadioCoordinator;
import com.yovo.lib_bluetooth.internal.common.bluetooth.radio.YoBtRadioLease;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android 真实 GATT 连接创建平台实现。
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidConnectionPlatform implements YoBleConnectionPlatform {

    private static final String TAG = "YoBleConnPlatform";

    private final Context appContext;
    private final YoBluetoothAdapterProvider adapterProvider;
    private final YoBleHiddenApiPolicy cacheRefreshPolicy;
    private final YoBtRadioCoordinator radioCoordinator;

    public YoBleAndroidConnectionPlatform(@NonNull Context appContext,
                                          @NonNull YoBluetoothAdapterProvider adapterProvider,
                                          @NonNull YoBleHiddenApiPolicy cacheRefreshPolicy,
                                          @NonNull YoBtRadioCoordinator radioCoordinator) {
        this.appContext = appContext.getApplicationContext();
        this.adapterProvider = Objects.requireNonNull(adapterProvider, "adapterProvider cannot be null");
        this.cacheRefreshPolicy = Objects.requireNonNull(cacheRefreshPolicy, "cacheRefreshPolicy cannot be null");
        this.radioCoordinator = Objects.requireNonNull(radioCoordinator, "radioCoordinator cannot be null");
    }

    @Nullable
    @Override
    public BleGattHandle connectGatt(@NonNull BleDeviceId deviceId,
                                     boolean autoConnect,
                                     @NonNull PlatformListener listener) {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null || !adapter.isEnabled()) {
            return null;
        }
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException e) {
            return null;
        }

        // 调用 RadioCoordinator.preemptForConnection 确保 cancelDiscovery 并等待底层就绪，避免 133 超时与射频冲突
        YoBtRadioLease radioLease = radioCoordinator.preemptForConnection("BleGattConnect:" + deviceId.value());
        AtomicBoolean leaseReleased = new AtomicBoolean(false);
        Runnable releaseLease = () -> {
            if (leaseReleased.compareAndSet(false, true)) {
                if (radioLease != null) {
                    radioLease.release();
                }
            }
        };

        PlatformListener coordinatedListener = new PlatformListener() {
            @Override
            public void onConnectionStateChange(int status, int newState) {
                if (newState == YoBleConnectionPlatform.STATE_CONNECTED || newState == YoBleConnectionPlatform.STATE_DISCONNECTED) {
                    releaseLease.run();
                }
                listener.onConnectionStateChange(status, newState);
            }

            @Override
            public void onServicesDiscovered(int status) {
                listener.onServicesDiscovered(status);
            }

            @Override
            public void onServiceChanged() {
                listener.onServiceChanged();
            }

            @Override
            public void onCharacteristicRead(@NonNull BleGattTarget target,
                                             @NonNull byte[] value, int status) {
                listener.onCharacteristicRead(target, value, status);
            }

            @Override
            public void onCharacteristicWrite(@NonNull BleGattTarget target,
                                              @Nullable byte[] echoedValue, int status) {
                listener.onCharacteristicWrite(target, echoedValue, status);
            }

            @Override
            public void onCharacteristicChanged(@NonNull BleGattTarget target,
                                                @NonNull byte[] value) {
                listener.onCharacteristicChanged(target, value);
            }

            @Override
            public void onDescriptorRead(@NonNull BleGattTarget target,
                                         @NonNull byte[] value, int status) {
                listener.onDescriptorRead(target, value, status);
            }

            @Override
            public void onDescriptorWrite(@NonNull BleGattTarget target, int status) {
                listener.onDescriptorWrite(target, status);
            }

            @Override
            public void onMtuChanged(int mtu, int status) {
                listener.onMtuChanged(mtu, status);
            }

            @Override
            public void onPhyUpdate(@NonNull BlePhyConfig phy, int status) {
                listener.onPhyUpdate(phy, status);
            }

            @Override
            public void onPhyRead(@NonNull BlePhyConfig phy, int status) {
                listener.onPhyRead(phy, status);
            }

            @Override
            public void onConnectionUpdated(int interval, int latency, int timeout, int status) {
                listener.onConnectionUpdated(interval, latency, timeout, status);
            }

            @Override
            public void onReadRemoteRssi(int rssi, int status) {
                listener.onReadRemoteRssi(rssi, status);
            }

            @Override
            public void onReliableWriteCompleted(int status) {
                listener.onReliableWriteCompleted(status);
            }
        };

        YoBleAndroidGattCallback cb = new YoBleAndroidGattCallback(coordinatedListener);

        BluetoothGatt gatt;
        try {
            gatt = YoBleAndroidGattCalls.connectLe(appContext, device, autoConnect, cb);
        } catch (SecurityException security) {
            releaseLease.run();
            throw security;
        } catch (Throwable t) {
            releaseLease.run();
            YoBtLogger.w(TAG, "connectGatt threw for " + deviceId, t);
            return null;
        }
        if (gatt == null) {
            releaseLease.run();
            return null;
        }
        YoBleAndroidGattTransport transport =
                new YoBleAndroidGattTransport(deviceId, gatt, cacheRefreshPolicy);
        AtomicBoolean closed = new AtomicBoolean(false);
        return new BleGattHandle() {
            @NonNull
            @Override
            public YoBleGattTransport transport() {
                return transport;
            }

            @Override
            public void disconnect() {
                if (!closed.get()) {
                    try {
                        gatt.disconnect();
                    } catch (Throwable t) {
                        YoBtLogger.w(TAG, "gatt.disconnect failed", t);
                    }
                }
            }

            @Override
            public void close() {
                if (closed.compareAndSet(false, true)) {
                    releaseLease.run();
                    try {
                        gatt.close();
                    } catch (Throwable t) {
                        YoBtLogger.w(TAG, "gatt.close failed", t);
                    }
                }
            }

            @Override
            public boolean isClosed() {
                return closed.get();
            }
        };
    }
}
