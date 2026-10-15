package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidProfileProxyAdapter;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 封装 Android Profile 代理请求/关闭及全局释放状态。
 */
final class YoCbtProfileProxyLifecycle {

    private static final String TAG = "YoCbtProfileProxyLifecycle";

    private final BluetoothAdapter adapter;
    private final AtomicBoolean released = new AtomicBoolean(false);

    YoCbtProfileProxyLifecycle(@NonNull BluetoothAdapter adapter) {
        this.adapter = adapter;
    }

    boolean isReleased() {
        return released.get();
    }

    @NonNull
    YoResult<Boolean> requestProxy(@NonNull Context appContext,
                                    @NonNull BluetoothProfile.ServiceListener listener,
                                    @NonNull YoCbtProfileType profileType) {
        if (released.get()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_PROXY_LIFECYCLE_ERROR,
                    "ProfileProxyManager has been released",
                    TAG));
        }
        return YoCbtAndroidProfileProxyAdapter.getProfileProxy(
                adapter, appContext, profileType.getAndroidProfile(), listener);
    }

    @NonNull
    YoResult<Void> closeProxy(@NonNull YoCbtProfileProxySession session) {
        session.markClosing();
        BluetoothProfile proxy = session.proxy();
        YoResult<Void> result = YoCbtAndroidProfileProxyAdapter.closeProfileProxy(
                adapter, session.profileType().getAndroidProfile(), proxy);
        session.markClosed();
        if (result.isFailure()) {
            YoBluetoothLogger.w(TAG, "关闭 ProfileProxy 失败，类型 " + session.profileType()
                    + "：" + result.getFailure().getMessage());
        }
        return YoResult.success(null);
    }

    void closeOrphanProxy(int androidProfile, @NonNull BluetoothProfile proxy) {
        YoCbtAndroidProfileProxyAdapter.closeProfileProxy(adapter, androidProfile, proxy);
    }

    void releaseAll(@NonNull YoCbtProfileProxyCache cache) {
        if (!released.compareAndSet(false, true)) return;
        for (YoCbtProfileProxySession session : cache.snapshot()) {
            closeProxy(session);
        }
        cache.clear();
    }
}
