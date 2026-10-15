package com.yovo.lib_common.system.permission.model;

/**
 * 权限状态。
 */
public enum PermissionStatus {
    GRANTED,
    DENIED,
    /** 当前系统版本不需要该权限（视为已满足）。 */
    NOT_REQUIRED
}
