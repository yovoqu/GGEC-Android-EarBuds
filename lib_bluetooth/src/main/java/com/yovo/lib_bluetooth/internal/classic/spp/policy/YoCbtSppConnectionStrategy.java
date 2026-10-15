package com.yovo.lib_bluetooth.internal.classic.spp.policy;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 定义 SPP 连接建立策略，决定端点解析方式及回退尝试顺序。
 */
public enum YoCbtSppConnectionStrategy {
    /** 仅使用 UUID 连接；无通道回退。 */
    UUID_ONLY,

    /** 仅使用 RFCOMM 通道连接；无 UUID 回退。 */
    CHANNEL_ONLY,

    /** 先尝试 UUID；UUID 失败后回退到通道。 */
    UUID_THEN_CHANNEL,

    /** 先尝试通道；通道失败后回退到 UUID。 */
    CHANNEL_THEN_UUID,

    /** 通过 UUID 经 SDP 解析服务，再在解析出的通道上连接。 */
    SDP_UUID_ONLY,

    /** 使用预先通过 SDP 解析的通道连接；无 UUID 回退。 */
    SDP_CHANNEL_ONLY,

    /** 仅使用用户指定的端点；无自动回退。 */
    USER_SPECIFIED_ONLY
}

