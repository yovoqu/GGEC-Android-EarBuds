package com.yovo.lib_bluetooth.internal.classic.profiles.core;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Profile 能力信息来源枚举（SDP、CoD、广播等）。
 */

public enum YoCbtCapabilitySource {
    SDP,
    CLASS_OF_DEVICE_HINT,
    ANDROID_PROFILE_PROXY,
    BONDED_CACHE,
    USER_CONFIG,
    VENDOR_CONFIG,
    DEVICE_VERIFICATION,
    SPP_CONNECTION_REGISTRY,
    UNKNOWN
}
