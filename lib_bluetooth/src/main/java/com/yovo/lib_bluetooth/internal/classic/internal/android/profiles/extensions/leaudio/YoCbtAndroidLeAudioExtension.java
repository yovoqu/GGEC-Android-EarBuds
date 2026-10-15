package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.leaudio;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.YoCbtAndroidTransientProfileProxyExtension;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android LE Audio Profile 扩展实现。
 */

public final class YoCbtAndroidLeAudioExtension extends YoCbtAndroidTransientProfileProxyExtension {

    @NonNull
    private final BluetoothAdapter adapter;

    public YoCbtAndroidLeAudioExtension(@NonNull Context context, @NonNull BluetoothAdapter adapter) {
        super(context, adapter);
        this.adapter = adapter;
    }

    @NonNull
    @Override
    public YoCbtProfileType getProfileType() {
        return YoCbtProfileType.LE_AUDIO;
    }

    @Override
    public boolean isSupported() {
        if (adapter == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
        try {
            return adapter.isLeAudioSupported() == BluetoothStatusCodes.FEATURE_SUPPORTED;
        } catch (NoSuchMethodError e) {
            return false;
        }
    }

    @Override
    protected int androidProfileId() {
        return YoCbtProfileType.LE_AUDIO.getAndroidProfile();
    }
}
