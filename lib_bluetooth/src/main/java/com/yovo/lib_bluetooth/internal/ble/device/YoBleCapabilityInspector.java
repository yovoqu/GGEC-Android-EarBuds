package com.yovo.lib_bluetooth.internal.ble.device;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothCapabilityChecker;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;

/**
 * BLE 硬件能力、蓝牙开关、位置开关与运行时权限环境检查端口（支持 JVM 假实现替换）。
 */
public final class YoBleCapabilityInspector {

    public interface EnvironmentPort {
        boolean isBleSupported();

        boolean isAdapterEnabled();

        boolean hasPermission(@NonNull YoBluetoothPermission permission);

        boolean isLocationServicesEnabled();

        int sdkInt();
    }

    @NonNull
    private final EnvironmentPort port;

    public YoBleCapabilityInspector(@NonNull Context context) {
        this(new AndroidEnvironmentPort(context));
    }

    public YoBleCapabilityInspector(@NonNull EnvironmentPort port) {
        this.port = port;
    }

    public boolean isBleSupported() {
        return port.isBleSupported();
    }

    public boolean isAdapterEnabled() {
        return port.isAdapterEnabled();
    }

    public boolean hasScanPermission() {
        return port.hasPermission(YoBluetoothPermission.SCAN);
    }

    public boolean hasConnectPermission() {
        return port.hasPermission(YoBluetoothPermission.CONNECT);
    }

    public boolean hasAdvertisePermission() {
        return port.hasPermission(YoBluetoothPermission.ADVERTISE);
    }

    public boolean hasLocationPermission() {
        return port.hasPermission(YoBluetoothPermission.LOCATION);
    }

    public boolean isLocationServicesEnabled() {
        return port.isLocationServicesEnabled();
    }

    public int sdkInt() {
        return port.sdkInt();
    }

    private static final class AndroidEnvironmentPort implements EnvironmentPort {
        private final Context appContext;
        private final YoBluetoothCapabilityChecker capabilityChecker;
        private final YoBluetoothAdapterProvider adapterProvider;
        private final YoBluetoothPermissionChecker permissionChecker;

        AndroidEnvironmentPort(@NonNull Context context) {
            this.appContext = context.getApplicationContext();
            this.capabilityChecker = new YoBluetoothCapabilityChecker(this.appContext);
            this.adapterProvider = new YoBluetoothAdapterProvider(this.appContext);
            this.permissionChecker = new YoBluetoothPermissionChecker();
        }

        @Override
        public boolean isBleSupported() {
            return capabilityChecker.supportsBle();
        }

        @Override
        public boolean isAdapterEnabled() {
            BluetoothAdapter adapter = adapterProvider.adapter();
            return adapter != null && adapter.isEnabled();
        }

        @Override
        public boolean hasPermission(@NonNull YoBluetoothPermission permission) {
            return permissionChecker.has(appContext, permission);
        }

        @Override
        public boolean isLocationServicesEnabled() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
                return lm != null && lm.isLocationEnabled();
            }
            LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return false;
            }
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }

        @Override
        public int sdkInt() {
            return Build.VERSION.SDK_INT;
        }
    }
}
