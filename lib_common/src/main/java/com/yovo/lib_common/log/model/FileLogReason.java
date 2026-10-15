package com.yovo.lib_common.log.model;

/**
 * 文件日志未启用/降级原因。
 */
public enum FileLogReason {
    /** 未请求文件日志或未初始化。 */
    NOT_REQUESTED,

    /** 权限/门禁拒绝。 */
    DENIED,

    /** IO 失败。 */
    IO_ERROR,

    /** 磁盘空间不足。 */
    LOW_DISK,

    /** 主路径不可用，已使用回退目录。 */
    PRIMARY_UNAVAILABLE_USING_FALLBACK,

    /** 已停机。 */
    SHUTDOWN
}
