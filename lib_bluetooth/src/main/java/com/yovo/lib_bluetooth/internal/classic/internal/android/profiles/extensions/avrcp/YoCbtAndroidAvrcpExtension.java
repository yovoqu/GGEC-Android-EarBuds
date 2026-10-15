package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.avrcp;

import android.bluetooth.BluetoothAdapter;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.YoCbtAndroidSdpUuidCapabilityExtension;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android A2DP Profile 扩展实现。
 */

public final class YoCbtAndroidAvrcpExtension extends YoCbtAndroidSdpUuidCapabilityExtension {

    private static final UUID AVRCP_CONTROLLER =
            UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB");
    private static final UUID AVRCP_TARGET =
            UUID.fromString("0000110C-0000-1000-8000-00805F9B34FB");

    public YoCbtAndroidAvrcpExtension(@NonNull BluetoothAdapter adapter) {
        super(adapter);
    }

    @NonNull
    @Override
    public YoCbtProfileType getProfileType() {
        return YoCbtProfileType.AVRCP;
    }

    @NonNull
    @Override
    protected UUID[] capabilityUuids() {
        return new UUID[] {AVRCP_CONTROLLER, AVRCP_TARGET};
    }
}
