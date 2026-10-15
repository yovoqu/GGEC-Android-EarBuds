package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy;

import android.bluetooth.BluetoothProfile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.proxy.YoCbtProfileProxyState;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单个 Profile 代理获取范围的引用计数会话。
 */
final class YoCbtProfileProxySession {

    private final YoCbtProfileProxyHandle handle;

    YoCbtProfileProxySession(@NonNull YoCbtProfileType profileType) {
        this.handle = new YoCbtProfileProxyHandle(profileType);
    }

    @NonNull
    YoCbtProfileType profileType() {
        return handle.profileType;
    }

    @NonNull
    YoCbtProfileProxyHandle handle() {
        return handle;
    }

    void acquire() {
        handle.refCount++;
    }

    /**
     * @return {@code true} when the underlying proxy should be closed
     */
    boolean release() {
        handle.refCount--;
        return handle.refCount <= 0;
    }

    void markRequesting() {
        handle.state = YoCbtProfileProxyState.REQUESTING;
    }

    void attachProxy(@NonNull BluetoothProfile proxy) {
        handle.proxy = proxy;
        handle.state = YoCbtProfileProxyState.CONNECTED;
    }

    void detachProxy() {
        handle.proxy = null;
        handle.state = YoCbtProfileProxyState.DISCONNECTED;
    }

    void markClosing() {
        handle.state = YoCbtProfileProxyState.CLOSING;
    }

    void markClosed() {
        handle.proxy = null;
        handle.state = YoCbtProfileProxyState.CLOSED;
        handle.refCount = 0;
    }

    @Nullable
    BluetoothProfile proxy() {
        return handle.proxy;
    }

    boolean isReady() {
        return handle.isReady();
    }

    boolean isRequesting() {
        return handle.state == YoCbtProfileProxyState.REQUESTING;
    }
}
