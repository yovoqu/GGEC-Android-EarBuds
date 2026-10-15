package com.yovo.lib_common.system.permission.model;

/**
 * 权限类型。
 */
public enum PermissionKind {
    /** 安装时权限（声明即授予，如 BLUETOOTH / BLUETOOTH_ADMIN）。 */
    INSTALL_TIME,
    /** 普通运行时权限（系统弹窗授权）。 */
    RUNTIME,
    /** 特殊访问权限（跳转系统设置页授权，如“所有文件访问”）。 */
    SPECIAL_ACCESS
}
