package com.yovo.lib_bluetooth.internal.classic.internal.android.spp;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidDeviceMapper;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP RemoteDevice 网关，封装 createRfcommSocket 等平台调用。
 */

@SuppressLint("MissingPermission")
public final class YoCbtSppRemoteDeviceGateway {

    private final YoBluetoothAdapterProvider adapterProvider;
    private final YoCbtAndroidDeviceMapper deviceMapper;

    public YoCbtSppRemoteDeviceGateway(@NonNull YoBluetoothAdapterProvider adapterProvider,
                                       @NonNull YoCbtAndroidDeviceMapper deviceMapper) {
        this.adapterProvider = Objects.requireNonNull(adapterProvider);
        this.deviceMapper = Objects.requireNonNull(deviceMapper);
    }

    public void cancelDiscovery() {
        if (adapterProvider.adapter() != null) {
            adapterProvider.adapter().cancelDiscovery();
        }
    }

    @Nullable
    public BluetoothDevice remoteDevice(@NonNull YoCbtDeviceId deviceId) {
        if (adapterProvider.adapter() == null) return null;
        return adapterProvider.adapter().getRemoteDevice(deviceId.value());
    }

    @Nullable
    public YoCbtDeviceIdentity resolveIdentity(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = remoteDevice(deviceId);
        return device == null ? null : deviceMapper.map(device, null);
    }

    @Nullable
    public YoCbtDeviceIdentity resolveIdentity(@NonNull BluetoothDevice device) {
        return deviceMapper.map(device, null);
    }
}
