package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.hearingaid;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.YoCbtAndroidTransientProfileProxyExtension;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android 助听（Hearing Aid）Profile 扩展实现。
 */

public final class YoCbtAndroidHearingAidExtension extends YoCbtAndroidTransientProfileProxyExtension {

    @NonNull
    private final BluetoothAdapter adapter;

    public YoCbtAndroidHearingAidExtension(@NonNull Context context, @NonNull BluetoothAdapter adapter) {
        super(context, adapter);
        this.adapter = adapter;
    }

    @NonNull
    @Override
    public YoCbtProfileType getProfileType() {
        return YoCbtProfileType.HEARING_AID;
    }

    @Override
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    public boolean isSupported() {
        return adapter != null && AndroidPlatform.supportsHearingAidProfile();
    }

    @Override
    protected int androidProfileId() {
        return YoCbtProfileType.HEARING_AID.getAndroidProfile();
    }
}
