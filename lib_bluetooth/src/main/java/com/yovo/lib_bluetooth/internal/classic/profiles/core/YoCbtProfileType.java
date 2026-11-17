package com.yovo.lib_bluetooth.internal.classic.profiles.core;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙 Profile/能力类型。HEADSET 是 Android 运行时连接 Profile；HFP/HSP
 * 仅表示 SDP 能力，不能分别查询连接状态。
 */

public enum YoCbtProfileType {
    SPP(-1),
    A2DP(2),
    HEADSET(1),
    HFP(-1),
    HSP(-1),
    AVRCP(-1, true),
    HID_HOST(4),
    PAN(5),
    PBAP(17),
    MAP(-1, true),
    OPP(-1, true),
    HEARING_AID(21),
    LE_AUDIO(22),
    UNKNOWN(-1);

    private final int androidProfile;
    private final boolean extensionOnly;

    YoCbtProfileType(int androidProfile) {
        this.androidProfile = androidProfile;
        this.extensionOnly = false;
    }

    YoCbtProfileType(int androidProfile, boolean extensionOnly) {
        this.androidProfile = androidProfile;
        this.extensionOnly = extensionOnly;
    }

    public int getAndroidProfile() {
        return androidProfile;
    }

    public boolean isExtensionOnly() {
        return extensionOnly;
    }

    public boolean hasAndroidProfile() {
        return androidProfile >= 0;
    }

    /** 是否只能作为设备能力使用，而不是独立的运行时连接状态。 */
    public boolean isCapabilityOnly() {
        return this == HFP || this == HSP;
    }

    @NonNull
    public static YoCbtProfileType fromAndroidProfile(int androidProfile) {
        for (YoCbtProfileType type : values()) {
            if (type.hasAndroidProfile() && type.androidProfile == androidProfile) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
