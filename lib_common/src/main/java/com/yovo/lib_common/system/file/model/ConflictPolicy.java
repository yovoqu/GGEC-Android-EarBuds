package com.yovo.lib_common.system.file.model;

/**
 * 目标已存在时的冲突策略。
 */
public enum ConflictPolicy {
    /** 覆盖已有目标。 */
    OVERWRITE,

    /** 目标已存在则失败（ALREADY_EXISTS）。 */
    FAIL,

    /** 自动追加序号生成唯一名（如 name(1).ext）。 */
    RENAME_UNIQUE
}
