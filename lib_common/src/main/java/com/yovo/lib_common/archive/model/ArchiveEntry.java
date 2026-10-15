package com.yovo.lib_common.archive.model;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 格式无关的归档条目描述。{@link #sizeHint()} 仅供参考，解压时不得作为安全边界。
 */
public final class ArchiveEntry {

    private final String name;
    private final long sizeHint;
    private final boolean directory;

    public ArchiveEntry(@NonNull String name, long sizeHint, boolean directory) {
        this.name = name;
        this.sizeHint = sizeHint;
        this.directory = directory;
    }

    @NonNull
    public String name() {
        return name;
    }

    /**
     * 条目声明的未压缩大小提示；不可信，实际写出由引擎自行计数。
     */
    public long sizeHint() {
        return sizeHint;
    }

    public boolean isDirectory() {
        return directory;
    }

    @NonNull
    @Override
    public String toString() {
        return "ArchiveEntry{name='" + name + "', sizeHint=" + sizeHint
                + ", directory=" + directory + '}';
    }
}
