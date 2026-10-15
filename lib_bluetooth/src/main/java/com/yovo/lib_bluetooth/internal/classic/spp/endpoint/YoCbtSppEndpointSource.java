package com.yovo.lib_bluetooth.internal.classic.spp.endpoint;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 端点来源枚举，记录端点来自 SDP、配置还是调用方指定。
 */

public enum YoCbtSppEndpointSource {
    USER_SPECIFIED,
    SDP_RESOLVED,
    DEFAULT_SPP,
    VENDOR_CONFIG,
    DEBUG_TOOL_CONFIRMED,
    BONDED_CACHE,
    UNKNOWN
}
