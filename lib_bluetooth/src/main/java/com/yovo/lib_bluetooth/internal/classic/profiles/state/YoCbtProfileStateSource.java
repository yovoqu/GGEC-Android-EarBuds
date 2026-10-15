package com.yovo.lib_bluetooth.internal.classic.profiles.state;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Profile 状态信息来源枚举（代理查询、系统广播等）。
 */

public enum YoCbtProfileStateSource {
    ANDROID_PROFILE_PROXY,
    PROFILE_BROADCAST,
    ACL_BROADCAST_INFERRED,
    SPP_CONNECTION_REGISTRY,
    UNKNOWN
}
