package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.opp;

import android.bluetooth.BluetoothAdapter;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.YoCbtAndroidSdpUuidCapabilityExtension;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android OPP Profile 扩展实现。
 */

public final class YoCbtAndroidOppExtension extends YoCbtAndroidSdpUuidCapabilityExtension {

    private static final UUID OPP_UUID = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB");

    public YoCbtAndroidOppExtension(@NonNull BluetoothAdapter adapter) {
        super(adapter);
    }

    @NonNull
    @Override
    public YoCbtProfileType getProfileType() {
        return YoCbtProfileType.OPP;
    }

    @NonNull
    @Override
    protected UUID[] capabilityUuids() {
        return new UUID[] {OPP_UUID};
    }
}
