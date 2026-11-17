package com.yovo.lib_bluetooth.internal.common.bluetooth.radio;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.Objects;

/**
 * Android 平台真实蓝牙硬件与适配器交互平台实现。
 */
@SuppressLint("MissingPermission")
public final class YoBtAndroidRadioPlatform implements YoBtRadioPlatform {

    private static final String TAG = "YoBtRadioPlatform";

    private final YoBluetoothAdapterProvider adapterProvider;

    public YoBtAndroidRadioPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider) {
        this.adapterProvider = Objects.requireNonNull(adapterProvider);
    }

    @Override
    public boolean isAdapterEnabled() {
        try {
            BluetoothAdapter adapter = adapterProvider.adapter();
            return adapter != null && adapter.isEnabled();
        } catch (SecurityException se) {
            YoBtLogger.w(TAG, "isAdapterEnabled rejected by permission check", se);
            return false;
        } catch (Throwable t) {
            YoBtLogger.w(TAG, "isAdapterEnabled failed", t);
            return false;
        }
    }

    @Override
    public boolean isDiscovering() {
        try {
            BluetoothAdapter adapter = adapterProvider.adapter();
            return adapter != null && adapter.isDiscovering();
        } catch (SecurityException se) {
            YoBtLogger.w(TAG, "isDiscovering rejected by permission check", se);
            return false;
        } catch (Throwable t) {
            YoBtLogger.w(TAG, "isDiscovering failed", t);
            return false;
        }
    }

    @Override
    public boolean cancelDiscovery() {
        try {
            BluetoothAdapter adapter = adapterProvider.adapter();
            if (adapter == null) {
                return false;
            }
            if (adapter.isDiscovering()) {
                return adapter.cancelDiscovery();
            }
            return adapter.cancelDiscovery();
        } catch (SecurityException se) {
            YoBtLogger.w(TAG, "cancelDiscovery rejected by permission check", se);
            return false;
        } catch (Throwable t) {
            YoBtLogger.w(TAG, "cancelDiscovery failed", t);
            return false;
        }
    }

    @Override
    public boolean startDiscovery() {
        try {
            BluetoothAdapter adapter = adapterProvider.adapter();
            return adapter != null && adapter.startDiscovery();
        } catch (SecurityException se) {
            YoBtLogger.w(TAG, "startDiscovery rejected by permission check", se);
            return false;
        } catch (Throwable t) {
            YoBtLogger.w(TAG, "startDiscovery failed", t);
            return false;
        }
    }
}
