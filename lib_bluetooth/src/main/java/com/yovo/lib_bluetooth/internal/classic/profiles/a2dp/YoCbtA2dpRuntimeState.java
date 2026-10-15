package com.yovo.lib_bluetooth.internal.classic.profiles.a2dp;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description A2DP 建链后的附加运行时状态。是否已连接必须读取
 * YoCbtA2dpDeviceState.profileState；本枚举不能替代 Profile 连接状态。
 */

public enum YoCbtA2dpRuntimeState {
    PROFILE_DISCONNECTED,
    PROFILE_CONNECTED,
    ACTIVE_DEVICE,
    CODEC_UNKNOWN
}
