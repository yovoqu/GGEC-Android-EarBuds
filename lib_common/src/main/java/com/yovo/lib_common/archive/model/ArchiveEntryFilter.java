package com.yovo.lib_common.archive.model;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 解压条目过滤器。返回 {@code false} 表示跳过该条目（不计入安全拒绝）。
 */
public interface ArchiveEntryFilter {

    /** 接受全部非目录条目。 */
    ArchiveEntryFilter ACCEPT_ALL = entry -> !entry.isDirectory();

    boolean accept(@NonNull ArchiveEntry entry);
}
