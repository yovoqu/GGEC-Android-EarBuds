package com.yovo.lib_bluetooth.internal.ble.device;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.location.LocationManager;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothCapabilityChecker;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.model.ble.BleAdapterState;

/**
 * {@link YoBleCapabilityInspector} 的 Android 实现：BLE 硬件能力、蓝牙开关、位置开关与运行时权限检查。
 */
public final class YoBleAndroidCapabilityInspector implements YoBleCapabilityInspector {

    private final Context appContext;
    private final YoBluetoothCapabilityChecker capabilityChecker;
    private final YoBluetoothAdapterProvider adapterProvider;
    private final YoBluetoothPermissionChecker permissionChecker;

    public YoBleAndroidCapabilityInspector(@NonNull Context context,
                                           @NonNull YoBluetoothPermissionChecker permissionChecker) {
        this.appContext = context.getApplicationContext();
        this.capabilityChecker = new YoBluetoothCapabilityChecker(this.appContext);
        this.adapterProvider = new YoBluetoothAdapterProvider(this.appContext);
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean isBleSupported() {
        return capabilityChecker.supportsBle();
    }

    @Override
    public boolean isAdapterEnabled() {
        return adapterState() == BleAdapterState.ON;
    }

    @Override
    @NonNull
    public BleAdapterState adapterState() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            return BleAdapterState.UNKNOWN;
        }
        switch (adapter.getState()) {
            case BluetoothAdapter.STATE_OFF:
                return BleAdapterState.OFF;
            case BluetoothAdapter.STATE_TURNING_ON:
                return BleAdapterState.TURNING_ON;
            case BluetoothAdapter.STATE_ON:
                return BleAdapterState.ON;
            case BluetoothAdapter.STATE_TURNING_OFF:
                return BleAdapterState.TURNING_OFF;
            default:
                return BleAdapterState.UNKNOWN;
        }
    }

    @Override
    public boolean hasScanPermission() {
        return permissionChecker.has(appContext, YoBluetoothPermission.SCAN);
    }

    @Override
    public boolean hasConnectPermission() {
        return permissionChecker.has(appContext, YoBluetoothPermission.CONNECT);
    }

    @Override
    public boolean hasAdvertisePermission() {
        return permissionChecker.has(appContext, YoBluetoothPermission.ADVERTISE);
    }

    @Override
    public boolean hasLocationPermission() {
        return permissionChecker.has(appContext, YoBluetoothPermission.LOCATION);
    }

    @Override
    public boolean isLocationServicesEnabled() {
        LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return false;
        }
        if (AndroidPlatform.supportsLocationEnabledApi()) {
            return lm.isLocationEnabled();
        }
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    @Override
    public int sdkInt() {
        return AndroidPlatform.level();
    }
}
