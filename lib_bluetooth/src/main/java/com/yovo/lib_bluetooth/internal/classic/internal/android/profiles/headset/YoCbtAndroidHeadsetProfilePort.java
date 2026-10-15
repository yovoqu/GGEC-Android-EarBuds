package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.headset;

import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy.YoCbtAndroidProfileProxyManager;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetProfilePort;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android Headset Profile 端口实现，对接 {@link YoCbtHeadsetProfilePort}。
 */

public final class YoCbtAndroidHeadsetProfilePort implements YoCbtHeadsetProfilePort {

    @NonNull
    private final YoCbtAndroidProfileProxyManager proxyManager;

    public YoCbtAndroidHeadsetProfilePort(@NonNull YoCbtAndroidProfileProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public boolean isReady() {
        return proxyManager.isProxyReady(YoCbtProfileType.HEADSET);
    }

    @NonNull
    @Override
    public List<YoCbtDeviceId> getConnectedDevices() {
        YoCbtAndroidHeadsetPlatformProxyAdapter adapter = resolveAdapter();
        return adapter == null ? Collections.emptyList() : adapter.getConnectedDevices();
    }

    @NonNull
    @Override
    public YoCbtProfileConnectionState getConnectionState(@NonNull YoCbtDeviceId deviceId) {
        YoCbtAndroidHeadsetPlatformProxyAdapter adapter = resolveAdapter();
        return adapter == null ? YoCbtProfileConnectionState.UNKNOWN : adapter.getConnectionState(deviceId);
    }

    @NonNull
    @Override
    public YoCbtHeadsetAudioState getAudioState(@NonNull YoCbtDeviceId deviceId) {
        YoCbtAndroidHeadsetPlatformProxyAdapter adapter = resolveAdapter();
        return adapter == null ? YoCbtHeadsetAudioState.UNKNOWN : adapter.getAudioState(deviceId);
    }

    @Nullable
    private YoCbtAndroidHeadsetPlatformProxyAdapter resolveAdapter() {
        BluetoothProfile profile = proxyManager.getProxy(YoCbtProfileType.HEADSET);
        if (profile == null) return null;
        return new YoCbtAndroidHeadsetPlatformProxyAdapter(profile);
    }
}
