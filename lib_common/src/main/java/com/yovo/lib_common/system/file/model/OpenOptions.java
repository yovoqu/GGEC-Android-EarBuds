package com.yovo.lib_common.system.file.model;

/**
 * 打开选项（可组合；写出时必须显式组合，默认不隐式 TRUNCATE 覆盖）。
 */
public enum OpenOptions {
    READ,
    WRITE,
    APPEND,
    TRUNCATE,
    CREATE,
    CREATE_NEW
}
