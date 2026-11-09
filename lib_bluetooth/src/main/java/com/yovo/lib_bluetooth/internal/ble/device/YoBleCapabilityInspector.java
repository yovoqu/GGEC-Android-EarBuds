package com.yovo.lib_bluetooth.internal.ble.device;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothCapabilityChecker;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;

/**
 * BLE 硬件能力、蓝牙开关、位置开关与运行时权限环境检查器。
 */
public final class YoBleCapabilityInspector {

    private final Context appContext;
    private final YoBluetoothCapabilityChecker capabilityChecker;
    private final YoBluetoothAdapterProvider adapterProvider;
    private final YoBluetoothPermissionChecker permissionChecker;

    public YoBleCapabilityInspector(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.capabilityChecker = new YoBluetoothCapabilityChecker(this.appContext);
        this.adapterProvider = new YoBluetoothAdapterProvider(this.appContext);
        this.permissionChecker = new YoBluetoothPermissionChecker();
    }

    public boolean isBleSupported() {
        return capabilityChecker.supportsBle();
    }

    public boolean isAdapterEnabled() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        return adapter != null && adapter.isEnabled();
    }

    public boolean hasScanPermission() {
        return permissionChecker.has(appContext, YoBluetoothPermission.SCAN);
    }

    public boolean hasConnectPermission() {
        return permissionChecker.has(appContext, YoBluetoothPermission.CONNECT);
    }

    public boolean hasAdvertisePermission() {
        return permissionChecker.has(appContext, YoBluetoothPermission.ADVERTISE);
    }

    public boolean hasLocationPermission() {
        return permissionChecker.has(appContext, YoBluetoothPermission.LOCATION);
    }

    public boolean isLocationServicesEnabled() {
        LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return lm.isLocationEnabled();
        }
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public int sdkInt() {
        return Build.VERSION.SDK_INT;
    }
}
