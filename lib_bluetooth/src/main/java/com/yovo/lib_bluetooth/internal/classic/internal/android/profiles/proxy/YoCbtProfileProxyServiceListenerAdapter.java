package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy;

import android.bluetooth.BluetoothProfile;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 Android {@link BluetoothProfile.ServiceListener} 回调适配为类型化代理事件。
 */
final class YoCbtProfileProxyServiceListenerAdapter implements BluetoothProfile.ServiceListener {

    interface Callback {
        void onServiceConnected(int androidProfile, @NonNull BluetoothProfile proxy);

        void onServiceDisconnected(int androidProfile);
    }

    private final Callback callback;

    YoCbtProfileProxyServiceListenerAdapter(@NonNull Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        if (proxy == null) return;
        callback.onServiceConnected(profile, proxy);
    }

    @Override
    public void onServiceDisconnected(int profile) {
        callback.onServiceDisconnected(profile);
    }
}
