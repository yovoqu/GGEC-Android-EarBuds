package com.yovo.lib_common.system.file.model;

/**
 * 统一文件失败码。
 */
public enum FileFailureCode {
    NOT_FOUND,
    DENIED,
    IO_ERROR,
    UNSUPPORTED,
    INVALID_TARGET,
    /** 相对路径逃逸根目录。 */
    TARGET_ESCAPE,
    SIZE_LIMIT_EXCEEDED,
    /** ConflictPolicy.FAIL 且目标已存在。 */
    ALREADY_EXISTS,
    /** 无法按策略解决命名冲突。 */
    CONFLICT,
    LOW_DISK
}
