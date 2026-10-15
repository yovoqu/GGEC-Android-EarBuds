package com.yovo.lib_bluetooth.internal.common.error;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 错误可恢复性分类枚举。
 */
public enum YoRecoverability {
    RECOVERABLE,
    NON_RECOVERABLE,
    RETRYABLE,
    FATAL
}
