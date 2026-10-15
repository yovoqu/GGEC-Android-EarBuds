package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy;

import android.bluetooth.BluetoothProfile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.proxy.YoCbtProfileProxyState;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单个 Profile 代理实例的可变持有者，含生命周期状态与引用计数。
 */
final class YoCbtProfileProxyHandle {

    @NonNull
    final YoCbtProfileType profileType;
    @Nullable
    BluetoothProfile proxy;
    @NonNull
    YoCbtProfileProxyState state;
    int refCount;

    YoCbtProfileProxyHandle(@NonNull YoCbtProfileType profileType) {
        this.profileType = profileType;
        this.state = YoCbtProfileProxyState.IDLE;
        this.refCount = 0;
    }

    boolean isReady() {
        return proxy != null && state == YoCbtProfileProxyState.CONNECTED;
    }
}
