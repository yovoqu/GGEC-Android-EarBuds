package com.yovo.lib_common.archive.model;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 解压布局策略。
 */
public enum UnpackLayout {

    /**
     * 仅使用条目 basename 写入目标目录，丢弃相对路径。入站分享等不可信源默认策略。
     */
    FLATTEN,

    /**
     * 保留相对路径，但每个条目仍经 PathSafety 校验，禁止逃逸目标目录。
     */
    PRESERVE_SAFE
}
