package com.yovo.lib_bluetooth.internal.classic.platform.profiles.extensions.leaudio;

import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.platform.profiles.extensions.YoCbtAndroidTransientProfileProxyExtension;
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
        // 版本门由 AndroidPlatform 单点表达；API 33+ 上 isLeAudioSupported() 必然存在，
        // 因此这里不吞 NoSuchMethodError：能力判断只有"平台能力 + 适配器回答"两个事实源。
        if (!AndroidPlatform.supportsLeAudioProfile()) {
            return false;
        }
        return adapter.isLeAudioSupported() == BluetoothStatusCodes.FEATURE_SUPPORTED;
    }

    @Override
    protected int androidProfileId() {
        return YoCbtProfileType.LE_AUDIO.getAndroidProfile();
    }
}
