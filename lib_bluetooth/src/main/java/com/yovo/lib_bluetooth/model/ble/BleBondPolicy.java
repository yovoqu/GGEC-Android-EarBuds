package com.yovo.lib_bluetooth.model.ble;

/**
 * 连接期间或遇到鉴权异常时的配对协调策略。
 */
public enum BleBondPolicy {
    /** 不自动发起配对。 */
    NONE,
    /** 发现服务前若未配对，先确保完成 Bond。 */
    ENSURE_BONDED_BEFORE_DISCOVERY,
    /** 当操作遭遇 ATT 认证/加密失败 (0x05/0x08/0x0F) 时自动触发配对并重试当前请求。 */
    AUTO_BOND_ON_AUTH_ERROR
}
