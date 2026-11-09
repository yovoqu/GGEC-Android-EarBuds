package com.yovo.lib_bluetooth.internal.ble.testing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.connection.YoBleConnectionPlatform;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BLE GATT 连接可控 Fake 平台：为每个 {@link BleDeviceId} 提供独立 {@link FakeBleGattDevice}。
 */
public final class FakeYoBleConnectionPlatform implements YoBleConnectionPlatform {

    private final Map<BleDeviceId, FakeBleGattDevice> devices = new LinkedHashMap<>();
    private boolean returnNullOnNextConnect = false;
    private int nullConnectsRemaining = 0;
    private final AtomicInteger totalConnectGattCalls = new AtomicInteger();

    @NonNull
    public synchronized FakeBleGattDevice device(@NonNull BleDeviceId deviceId) {
        return devices.computeIfAbsent(deviceId, FakeBleGattDevice::new);
    }

    public synchronized FakeYoBleConnectionPlatform register(@NonNull BleDeviceId deviceId,
                                                             @NonNull FakeBleGattDevice dev) {
        devices.put(deviceId, dev);
        return this;
    }

    public synchronized FakeYoBleConnectionPlatform setReturnNullConnects(int count) {
        this.nullConnectsRemaining = Math.max(0, count);
        this.returnNullOnNextConnect = count > 0;
        return this;
    }

    public int totalConnectGattCalls() {
        return totalConnectGattCalls.get();
    }

    @Nullable
    @Override
    public BleGattHandle connectGatt(@NonNull BleDeviceId deviceId,
                                     boolean autoConnect,
                                     @NonNull PlatformListener listener) {
        totalConnectGattCalls.incrementAndGet();
        synchronized (this) {
            if (returnNullOnNextConnect && nullConnectsRemaining > 0) {
                nullConnectsRemaining--;
                if (nullConnectsRemaining == 0) {
                    returnNullOnNextConnect = false;
                }
                return null;
            }
        }
        return device(deviceId).acceptConnect(listener);
    }
}
