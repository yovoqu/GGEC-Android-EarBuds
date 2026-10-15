package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.hid;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.YoCbtAndroidTransientProfileProxyExtension;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android HID Profile 扩展实现。
 */

@SuppressLint("MissingPermission")
public final class YoCbtAndroidHidExtension extends YoCbtAndroidTransientProfileProxyExtension {

    private static final UUID HID_UUID = UUID.fromString("00001124-0000-1000-8000-00805F9B34FB");

    @NonNull
    private final BluetoothAdapter adapter;

    public YoCbtAndroidHidExtension(@NonNull Context context, @NonNull BluetoothAdapter adapter) {
        super(context, adapter);
        this.adapter = adapter;
    }

    @NonNull
    @Override
    public YoCbtProfileType getProfileType() {
        return YoCbtProfileType.HID_HOST;
    }

    @Override
    protected int androidProfileId() {
        return YoCbtProfileType.HID_HOST.getAndroidProfile();
    }

    @NonNull
    @Override
    public YoResult<Boolean> hasCapability(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = adapter.getRemoteDevice(deviceId.value());
        ParcelUuid[] uuids = device.getUuids();
        if (uuids == null) {
            return YoResult.success(false);
        }
        for (ParcelUuid uuid : uuids) {
            if (HID_UUID.equals(uuid.getUuid())) {
                return YoResult.success(true);
            }
        }
        return YoResult.success(false);
    }
}
