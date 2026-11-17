package com.yovo.lib_bluetooth.internal.classic.platform.discovery;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.bluetooth.radio.YoBtRadioCoordinator;
import com.yovo.lib_bluetooth.spi.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryPlatform;

import java.util.Objects;

/**
 * Android 经典蓝牙发现 {@link YoCbtDiscoveryPlatform} 实现。
 */
@SuppressLint("MissingPermission")
public final class YoCbtAndroidDiscoveryPlatform implements YoCbtDiscoveryPlatform {

    private final YoBluetoothAdapterProvider adapterProvider;
    private final YoBluetoothPermissionChecker permissionChecker;
    private final YoBtRadioCoordinator radioCoordinator;

    public YoCbtAndroidDiscoveryPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider,
                                         @NonNull YoBluetoothPermissionChecker permissionChecker,
                                         @NonNull YoBtRadioCoordinator radioCoordinator) {
        this.adapterProvider = Objects.requireNonNull(adapterProvider, "adapterProvider cannot be null");
        this.permissionChecker = Objects.requireNonNull(permissionChecker, "permissionChecker cannot be null");
        this.radioCoordinator = Objects.requireNonNull(radioCoordinator, "radioCoordinator cannot be null");
    }

    @Override
    public boolean isSupported() {
        return adapterProvider.adapter() != null;
    }

    @Override
    public boolean isEnabled() {
        return radioCoordinator.getRadioPlatform().isAdapterEnabled();
    }

    @Override
    public boolean isDiscovering() {
        return radioCoordinator.getRadioPlatform().isDiscovering();
    }

    @Override
    public boolean startDiscovery() {
        if (!radioCoordinator.isClassicDiscoveryAllowed()) {
            return false;
        }
        return radioCoordinator.getRadioPlatform().startDiscovery();
    }

    @Override
    public void cancelDiscovery() {
        radioCoordinator.cancelDiscoverySafely();
    }

    @Override
    public boolean requiresLocationPermission() {
        return !AndroidPlatform.supportsModernDiscovery();
    }

    @Override
    public boolean hasLocationPermission(@NonNull Context context) {
        return permissionChecker.has(context, YoBluetoothPermission.LOCATION);
    }
}
