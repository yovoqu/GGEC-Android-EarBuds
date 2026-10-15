package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.map;

import android.bluetooth.BluetoothAdapter;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.YoCbtAndroidSdpUuidCapabilityExtension;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android MAP Profile 扩展实现。
 */

public final class YoCbtAndroidMapExtension extends YoCbtAndroidSdpUuidCapabilityExtension {

    private static final UUID MAP_UUID_MAS = UUID.fromString("00001132-0000-1000-8000-00805F9B34FB");
    private static final UUID MAP_UUID_MNS = UUID.fromString("00001133-0000-1000-8000-00805F9B34FB");

    public YoCbtAndroidMapExtension(@NonNull BluetoothAdapter adapter) {
        super(adapter);
    }

    @NonNull
    @Override
    public YoCbtProfileType getProfileType() {
        return YoCbtProfileType.MAP;
    }

    @NonNull
    @Override
    protected UUID[] capabilityUuids() {
        return new UUID[] {MAP_UUID_MAS, MAP_UUID_MNS};
    }
}
