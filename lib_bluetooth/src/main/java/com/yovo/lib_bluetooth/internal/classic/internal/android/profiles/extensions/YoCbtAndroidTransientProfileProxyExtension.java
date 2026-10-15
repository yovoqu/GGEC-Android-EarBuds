package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.extensions.YoCbtProfileExtension;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 需临时获取 Profile 代理的扩展基类。
 */

@SuppressLint("MissingPermission")
public abstract class YoCbtAndroidTransientProfileProxyExtension implements YoCbtProfileExtension {

    @NonNull
    private final Context appContext;
    @NonNull
    private final BluetoothAdapter adapter;

    public YoCbtAndroidTransientProfileProxyExtension(@NonNull Context context,
                                               @NonNull BluetoothAdapter adapter) {
        this.appContext = context.getApplicationContext();
        this.adapter = adapter;
    }

    protected abstract int androidProfileId();

    @Override
    public boolean isSupported() {
        return adapter != null;
    }

    @NonNull
    @Override
    public YoResult<Boolean> isConnected(@NonNull YoCbtDeviceId deviceId) {
        try {
            int state = adapter.getProfileConnectionState(androidProfileId());
            return YoResult.success(state == BluetoothProfile.STATE_CONNECTED);
        } catch (Throwable ignored) {
            return YoResult.success(false);
        }
    }

    @Override
    public void release() {
    }
}
