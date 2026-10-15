package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.extensions.YoCbtProfileExtension;

import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 基于 SDP UUID 的 Profile 能力扩展抽象基类。
 */

@SuppressLint("MissingPermission")
public abstract class YoCbtAndroidSdpUuidCapabilityExtension implements YoCbtProfileExtension {

    @NonNull
    private final BluetoothAdapter adapter;

    protected YoCbtAndroidSdpUuidCapabilityExtension(@NonNull BluetoothAdapter adapter) {
        this.adapter = adapter;
    }

    @NonNull
    protected abstract UUID[] capabilityUuids();

    @Override
    public boolean isSupported() {
        return adapter != null;
    }

    @NonNull
    @Override
    public YoResult<Boolean> hasCapability(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = adapter.getRemoteDevice(deviceId.value());
        ParcelUuid[] uuids = device.getUuids();
        if (uuids == null) {
            return YoResult.success(false);
        }
        UUID[] expected = capabilityUuids();
        for (ParcelUuid parcelUuid : uuids) {
            UUID parsed = parcelUuid.getUuid();
            for (UUID expectedUuid : expected) {
                if (expectedUuid.equals(parsed)) {
                    return YoResult.success(true);
                }
            }
        }
        return YoResult.success(false);
    }

    @NonNull
    @Override
    public YoResult<Boolean> isConnected(@NonNull YoCbtDeviceId deviceId) {
        return YoResult.success(false);
    }

    @Override
    public void release() {
    }
}
