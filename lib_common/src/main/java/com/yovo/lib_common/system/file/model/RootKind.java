package com.yovo.lib_common.system.file.model;

/**
 * APP_* 范围下根目录的选取种类。
 */
public enum RootKind {
    /** Context.getFilesDir()。 */
    FILES,

    /** Context.getCacheDir()。 */
    CACHE,

    /** Context.getExternalFilesDir(null)。 */
    EXT_FILES,

    /** Context.getExternalCacheDir()。 */
    EXT_CACHE,

    /** 宿主显式传入的自定义根目录（仅 APP_INTERNAL 语义，不做权限门禁）。 */
    CUSTOM_SUBDIR
}
