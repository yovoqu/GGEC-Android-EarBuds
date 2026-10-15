package com.yovo.lib_bluetooth.model.classic;

/**
 * SPP 连接策略：决定端点类型约束与回退顺序。
 */
public enum CbtSppConnectionStrategy {
    /** 仅使用 UUID 连接；无通道回退。 */
    UUID_ONLY,

    /** 仅使用 RFCOMM 通道连接；无 UUID 回退。 */
    CHANNEL_ONLY,

    /** 先尝试 UUID；失败后回退到通道。 */
    UUID_THEN_CHANNEL,

    /** 先尝试通道；失败后回退到 UUID。 */
    CHANNEL_THEN_UUID,

    /** 通过 UUID 经 SDP 解析服务，再在解析出的通道上连接。 */
    SDP_UUID_ONLY,

    /** 使用预先通过 SDP 解析的通道连接；无 UUID 回退。 */
    SDP_CHANNEL_ONLY,

    /** 仅使用调用方指定的端点；无自动回退。 */
    USER_SPECIFIED_ONLY
}
