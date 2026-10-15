package com.yovo.lib_common.system.file.model;

/**
 * 访问被拒的原因分类。
 */
public enum AccessDenyReason {
    /** 缺少 STORAGE_DIRECT 策略要求的权限（多用于 ABSOLUTE 路径）。 */
    MISSING_STORAGE_DIRECT,

    /** URI 从未获得授权（非持久化或临时授权不可用）。 */
    URI_PERMISSION_MISSING,

    /** 持久化授权已丢失/过期。 */
    URI_PERMISSION_LOST,

    /** 目标本身非法（构造/解析失败）。 */
    INVALID_TARGET,

    /** 外部存储介质当前不可用。 */
    EXTERNAL_MEDIA_UNAVAILABLE,

    /** 可用空间低于门禁阈值（可选项，通常由 IO 失败码报告）。 */
    LOW_DISK,

    /** SAF 文档不支持写（COLUMN_FLAGS 缺少 FLAG_SUPPORTS_WRITE）。 */
    SAF_NOT_WRITABLE
}
