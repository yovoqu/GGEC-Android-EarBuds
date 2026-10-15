package com.yovo.lib_bluetooth.internal.classic.spp.endpoint;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 端点解析的连接策略枚举。
 */
public enum YoCbtSppConnectStrategy {
    /** 仅通过 UUID 连接（标准 SDP 方式）。 */
    UUID_ONLY,
    /** 仅通过原始 RFCOMM 通道连接。 */
    CHANNEL_ONLY,
    /** 先尝试 UUID，失败后回退到通道。 */
    UUID_THEN_CHANNEL,
    /** 先尝试通道，失败后回退到 UUID。 */
    CHANNEL_THEN_UUID,
    /** 仅使用 SDP 解析得到的 UUID。 */
    SDP_UUID_ONLY,
    /** 仅使用 SDP 解析得到的通道。 */
    SDP_CHANNEL_ONLY,
    /** 使用调用方指定的端点，不进行 SDP 解析。 */
    USER_SPECIFIED_ONLY
}
