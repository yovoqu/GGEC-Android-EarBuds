package com.yovo.lib_bluetooth.internal.classic.internal.android.discovery;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryPlatform;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android 经典蓝牙发现 {@link YoCbtDiscoveryPlatform} 实现。
 */

@SuppressLint("MissingPermission")
public final class YoCbtAndroidDiscoveryPlatform implements YoCbtDiscoveryPlatform {

    private final YoBluetoothAdapterProvider adapterProvider;
    private final YoBluetoothPermissionChecker permissionChecker;

    public YoCbtAndroidDiscoveryPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider,
                                         @NonNull YoBluetoothPermissionChecker permissionChecker) {
        this.adapterProvider = Objects.requireNonNull(adapterProvider);
        this.permissionChecker = Objects.requireNonNull(permissionChecker);
    }

    @Override
    public boolean isSupported() {
        return adapterProvider.adapter() != null;
    }

    @Override
    public boolean isEnabled() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        return adapter != null && adapter.isEnabled();
    }

    @Override
    public boolean isDiscovering() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        return adapter != null && adapter.isDiscovering();
    }

    @Override
    public boolean startDiscovery() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        return adapter != null && adapter.startDiscovery();
    }

    @Override
    public void cancelDiscovery() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) return;
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
    }

    @Override
    public boolean requiresLegacyLocationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S;
    }

    @Override
    public boolean hasLegacyLocationPermission(@NonNull Context context) {
        return permissionChecker.has(context, YoBluetoothPermission.LOCATION);
    }
}
