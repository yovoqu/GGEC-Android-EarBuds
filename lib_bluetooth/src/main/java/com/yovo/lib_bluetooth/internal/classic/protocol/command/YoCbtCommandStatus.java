package com.yovo.lib_bluetooth.internal.classic.protocol.command;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 命令状态枚举（待处理、已发送、已完成、已超时等）。
 */

public enum YoCbtCommandStatus {
    QUEUED,
    SENT,
    RESPONSE_RECEIVED,
    TIMEOUT,
    FAILED
}
