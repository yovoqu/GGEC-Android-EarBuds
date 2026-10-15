package com.yovo.lib_common.system.file.model;

/**
 * 路径类目标的根范围。仅用于 {@link PathTarget}，URI 目标不适用。
 */
public enum FileScope {
    /** 应用内部目录（files / cache / 自定义子目录）。 */
    APP_INTERNAL,

    /** 应用外部包目录（getExternalFilesDir / getExternalCacheDir）。 */
    APP_EXTERNAL,

    /** 系统绝对路径。 */
    ABSOLUTE
}
