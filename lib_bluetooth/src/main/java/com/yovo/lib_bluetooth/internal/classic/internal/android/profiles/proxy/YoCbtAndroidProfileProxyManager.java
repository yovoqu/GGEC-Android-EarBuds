package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.YoCbtAudioProfileConnect;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android Profile 代理管理器，协调生命周期、缓存与会话。
 */

public final class YoCbtAndroidProfileProxyManager {

    private static final String TAG = "YoCbtAndroidProfileProxyManager";

    private final Context appContext;
    private final BluetoothAdapter adapter;
    private final YoCbtProfileProxyCache cache = new YoCbtProfileProxyCache();
    @Nullable
    private YoCbtDeviceId pendingAudio;
    private final YoCbtProfileProxyLifecycle lifecycle;
    private final YoCbtProfileProxyServiceListenerAdapter serviceListenerAdapter;

    public YoCbtAndroidProfileProxyManager(
            @NonNull Context appContext,
            @NonNull BluetoothAdapter adapter) {
        this.appContext = appContext.getApplicationContext();
        this.adapter = adapter;
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

    /**
     * 请求 A2DP 与 Headset 的隐藏 {@code connect}。代理还没到就先记下地址，
     * 任一代理连上后再发。两侧都拒绝才失败。
     */
    @NonNull
    public synchronized YoResult<Void> connectAudio(@NonNull YoCbtDeviceId deviceId) {
        if (adapter == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BLUETOOTH_NOT_SUPPORTED,
                    "BluetoothAdapter is unavailable",
                    TAG));
        }
        BluetoothDevice device = resolve(deviceId);
        if (device == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BONDED_DEVICE_NOT_FOUND,
                    "Cannot resolve BluetoothDevice from device ID: " + deviceId,
                    TAG));
        }
        pendingAudio = deviceId;
        return dispatchPending();
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
        pendingAudio = null;
        lifecycle.releaseAll(cache);
    }

    @NonNull
    private YoResult<Void> dispatchPending() {
        if (pendingAudio == null) {
            return YoResult.success(null);
        }
        BluetoothDevice device = resolve(pendingAudio);
        if (device == null) {
            pendingAudio = null;
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BONDED_DEVICE_NOT_FOUND,
                    "Cannot resolve BluetoothDevice for pending audio connect",
                    TAG));
        }
        BluetoothProfile a2dp = getProxy(YoCbtProfileType.A2DP);
        BluetoothProfile headset = getProxy(YoCbtProfileType.HEADSET);
        if (a2dp == null && headset == null) {
            return YoResult.success(null);
        }
        boolean accepted = YoCbtAudioProfileConnect.request(a2dp, headset, device);
        boolean bothPresent = a2dp != null && headset != null;
        if (accepted || bothPresent) {
            pendingAudio = null;
        }
        if (accepted || !bothPresent) {
            return YoResult.success(null);
        }
        return YoResult.failure(YoCbtFailure.of(
                YoCbtErrorCode.YO_CBT_AUDIO_CONNECT_REJECTED,
                "A2DP and Headset connect() both rejected the request",
                TAG));
    }

    @Nullable
    private BluetoothDevice resolve(@NonNull YoCbtDeviceId deviceId) {
        if (adapter == null) {
            return null;
        }
        try {
            return adapter.getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException e) {
            return null;
        }
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
            dispatchPending();
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
