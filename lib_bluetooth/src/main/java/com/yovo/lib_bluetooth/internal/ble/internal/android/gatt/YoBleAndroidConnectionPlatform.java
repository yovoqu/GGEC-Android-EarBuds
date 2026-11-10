package com.yovo.lib_bluetooth.internal.ble.internal.android.gatt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.connection.YoBleConnectionPlatform;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattTransport;
import com.yovo.lib_bluetooth.internal.ble.internal.android.compat.YoBleGattCompat;
import com.yovo.lib_bluetooth.internal.ble.internal.android.hiddenapi.YoBleHiddenApiPolicy;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;

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

    public YoBleAndroidConnectionPlatform(@NonNull Context appContext,
                                          @NonNull YoBluetoothAdapterProvider adapterProvider,
                                          @NonNull YoBleHiddenApiPolicy cacheRefreshPolicy) {
        this.appContext = appContext.getApplicationContext();
        this.adapterProvider = adapterProvider;
        this.cacheRefreshPolicy = cacheRefreshPolicy;
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
        YoBleAndroidGattCallbackBridge cb = new YoBleAndroidGattCallbackBridge(listener);
        BluetoothGatt gatt;
        try {
            gatt = YoBleGattCompat.connectLe(appContext, device, autoConnect, cb);
        } catch (SecurityException security) {
            throw security;
        } catch (Throwable t) {
            YoBluetoothLogger.w(TAG, "connectGatt threw for " + deviceId, t);
            return null;
        }
        if (gatt == null) {
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
                    } catch (Throwable ignored) {
                    }
                }
            }

            @Override
            public void close() {
                if (closed.compareAndSet(false, true)) {
                    try {
                        gatt.close();
                    } catch (Throwable ignored) {
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
