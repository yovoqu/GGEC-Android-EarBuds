package com.yovo.lib_bluetooth.internal.ble.testing;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;

import java.util.EnumSet;
import java.util.Set;

/**
 * BLE 环境与权限可控 Fake 实现，用于测试 Android API 24–37 权限与位置服务检查矩阵。
 */
public final class FakeYoBleEnvironmentPort implements YoBleCapabilityInspector.EnvironmentPort {

    private boolean bleSupported = true;
    private boolean adapterEnabled = true;
    private boolean locationServicesEnabled = true;
    private int sdkInt = 34;
    private final Set<YoBluetoothPermission> granted = EnumSet.allOf(YoBluetoothPermission.class);

    @Override
    public boolean isBleSupported() {
        return bleSupported;
    }

    public FakeYoBleEnvironmentPort setBleSupported(boolean bleSupported) {
        this.bleSupported = bleSupported;
        return this;
    }

    @Override
    public boolean isAdapterEnabled() {
        return adapterEnabled;
    }

    public FakeYoBleEnvironmentPort setAdapterEnabled(boolean adapterEnabled) {
        this.adapterEnabled = adapterEnabled;
        return this;
    }

    @Override
    public boolean hasPermission(@NonNull YoBluetoothPermission permission) {
        return granted.contains(permission);
    }

    public FakeYoBleEnvironmentPort grant(@NonNull YoBluetoothPermission permission) {
        granted.add(permission);
        return this;
    }

    public FakeYoBleEnvironmentPort deny(@NonNull YoBluetoothPermission permission) {
        granted.remove(permission);
        return this;
    }

    public FakeYoBleEnvironmentPort grantAll() {
        granted.addAll(EnumSet.allOf(YoBluetoothPermission.class));
        return this;
    }

    public FakeYoBleEnvironmentPort denyAll() {
        granted.clear();
        return this;
    }

    @Override
    public boolean isLocationServicesEnabled() {
        return locationServicesEnabled;
    }

    public FakeYoBleEnvironmentPort setLocationServicesEnabled(boolean locationServicesEnabled) {
        this.locationServicesEnabled = locationServicesEnabled;
        return this;
    }

    @Override
    public int sdkInt() {
        return sdkInt;
    }

    public FakeYoBleEnvironmentPort setSdkInt(int sdkInt) {
        this.sdkInt = sdkInt;
        return this;
    }
}
