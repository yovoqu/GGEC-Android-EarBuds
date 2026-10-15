package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.pan;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.YoCbtAndroidTransientProfileProxyExtension;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android PAN Profile 扩展实现。
 */

public final class YoCbtAndroidPanExtension extends YoCbtAndroidTransientProfileProxyExtension {

    public YoCbtAndroidPanExtension(@NonNull Context context, @NonNull BluetoothAdapter adapter) {
        super(context, adapter);
    }

    @NonNull
    @Override
    public YoCbtProfileType getProfileType() {
        return YoCbtProfileType.PAN;
    }

    @Override
    protected int androidProfileId() {
        return YoCbtProfileType.PAN.getAndroidProfile();
    }
}
