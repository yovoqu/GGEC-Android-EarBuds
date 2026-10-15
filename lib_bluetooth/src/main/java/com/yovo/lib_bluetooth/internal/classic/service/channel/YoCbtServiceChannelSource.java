package com.yovo.lib_bluetooth.internal.classic.service.channel;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 与服务 UUID 关联的 RFCOMM 信道号来源枚举。
 */
public enum YoCbtServiceChannelSource {
    USER_SPECIFIED,
    SDP_RESOLVED,
    VENDOR_CONFIG,
    DEBUG_TOOL_CONFIRMED,
    BONDED_CACHE,
    UNKNOWN
}
