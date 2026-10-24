package com.yovo.lib_ai.error;

/**
 * 调用方真正关心的失败分类。HTTP 状态码、传输异常和协议异常都折成这几类，界面据此给出处理建议。
 */
public enum YoAiErrorKind {
    /** 密钥缺失、无效或已过期。 */
    AUTHENTICATION,
    /** 密钥有效但无权访问该模型或资源。 */
    PERMISSION,
    /** 余额或额度用尽。 */
    QUOTA,
    /** 触发限流，稍后重试。 */
    RATE_LIMITED,
    /** 路径或模型不存在。 */
    NOT_FOUND,
    /** 请求本身被拒绝：参数、长度或内容不合法。 */
    BAD_REQUEST,
    /** 平台侧故障或过载。 */
    SERVER,
    /** 连不上主机、DNS 或 TLS 失败。 */
    NETWORK,
    /** 连接或读取超时。 */
    TIMEOUT,
    /** 调用方主动取消。 */
    CANCELLED,
    /** 响应不符合协议，通常是地址不是该协议的端点。 */
    PROTOCOL,
    /**
     * 这台平台给不出所需能力（没有对应的服务，或列出的模型都不具备该能力）。
     * 与 {@link #BAD_REQUEST} 分开：这不是参数写错了，换参数重发也不会成。
     */
    UNSUPPORTED_CAPABILITY,
    UNKNOWN
}
