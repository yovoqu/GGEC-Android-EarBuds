package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android Profile 代理管理器，协调生命周期、缓存与会话。
 */

public final class YoCbtAndroidProfileProxyManager {

    private static final String TAG = "YoCbtAndroidProfileProxyManager";

    private final Context appContext;
    private final YoCbtProfileProxyCache cache = new YoCbtProfileProxyCache();
    private final YoCbtProfileProxyLifecycle lifecycle;
    private final YoCbtProfileProxyServiceListenerAdapter serviceListenerAdapter;

    public YoCbtAndroidProfileProxyManager(
            @NonNull Context appContext,
            @NonNull BluetoothAdapter adapter) {
        this.appContext = appContext.getApplicationContext();
        this.lifecycle = new YoCbtProfileProxyLifecycle(adapter);
        this.serviceListenerAdapter = new YoCbtProfileProxyServiceListenerAdapter(
                new YoCbtProfileProxyServiceListenerAdapter.Callback() {
                    @Override
                    public void onServiceConnected(int androidProfile, @NonNull BluetoothProfile proxy) {
                        YoCbtAndroidProfileProxyManager.this.onServiceConnected(androidProfile, proxy);
                    }

                    @Override
                    public void onServiceDisconnected(int androidProfile) {
                        YoCbtAndroidProfileProxyManager.this.onServiceDisconnected(androidProfile);
                    }
                });
    }

    @NonNull
    public synchronized YoResult<Void> acquireProxy(@NonNull YoCbtProfileType profileType) {
        if (lifecycle.isReleased()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_PROXY_LIFECYCLE_ERROR,
                    "ProfileProxyManager has been released",
                    TAG));
        }
        if (!profileType.hasAndroidProfile()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_UNSUPPORTED,
                    "Profile type " + profileType + " has no Android proxy",
                    TAG));
        }

        YoCbtProfileProxySession session = cache.get(profileType);
        if (session != null) {
            session.acquire();
            if (session.isReady() || session.isRequesting()) {
                return YoResult.success(null);
            }
        } else {
            session = cache.create(profileType);
            session.acquire();
        }

        YoResult<Boolean> started = lifecycle.requestProxy(
                appContext, serviceListenerAdapter, profileType);
        if (started.isFailure()) {
            cache.remove(profileType);
            return YoResult.failure(started.getFailure());
        }
        if (!Boolean.TRUE.equals(started.getValue())) {
            cache.remove(profileType);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_PROXY_GET_FAILED,
                    "getProfileProxy returned false for " + profileType,
                    TAG));
        }

        session.markRequesting();
        return YoResult.success(null);
    }

    @NonNull
    public synchronized YoResult<Void> releaseProxy(@NonNull YoCbtProfileType profileType) {
        YoCbtProfileProxySession session = cache.get(profileType);
        if (session == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_PROXY_LIFECYCLE_ERROR,
                    "Proxy for " + profileType + " was not acquired",
                    TAG));
        }

        boolean shouldClose = session.release();
        if (shouldClose) {
            lifecycle.closeProxy(session);
            cache.remove(profileType);
        }
        return YoResult.success(null);
    }

    @Nullable
    public synchronized BluetoothProfile getProxy(@NonNull YoCbtProfileType profileType) {
        YoCbtProfileProxySession session = cache.get(profileType);
        return session == null ? null : session.proxy();
    }

    public synchronized boolean isProxyReady(@NonNull YoCbtProfileType profileType) {
        YoCbtProfileProxySession session = cache.get(profileType);
        return session != null && session.isReady();
    }

    public synchronized void releaseAll() {
        lifecycle.releaseAll(cache);
    }

    private void onServiceConnected(int androidProfile, @NonNull BluetoothProfile proxy) {
        YoCbtProfileType profileType = YoCbtProfileType.fromAndroidProfile(androidProfile);
        synchronized (this) {
            YoCbtProfileProxySession session = cache.get(profileType);
            if (session == null) {
                lifecycle.closeOrphanProxy(androidProfile, proxy);
                return;
            }
            session.attachProxy(proxy);
        }
    }

    private void onServiceDisconnected(int androidProfile) {
        YoCbtProfileType profileType = YoCbtProfileType.fromAndroidProfile(androidProfile);
        synchronized (this) {
            YoCbtProfileProxySession session = cache.get(profileType);
            if (session != null) {
                session.detachProxy();
            }
        }
    }
}
