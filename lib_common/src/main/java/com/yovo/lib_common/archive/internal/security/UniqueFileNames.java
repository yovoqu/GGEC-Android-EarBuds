package com.yovo.lib_common.archive.internal.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 同名文件冲突处理：同时考虑磁盘已存在文件与本次操作已占用名。
 */
public final class UniqueFileNames {

    private final Set<String> used = new HashSet<>();

    /**
     * 在目标目录内分配不冲突的文件：{@code name.ext} → {@code name(1).ext}。
     */
    @NonNull
    public File allocate(@NonNull File dir, @Nullable String originalName) {
        String base = (originalName == null || originalName.isEmpty()) ? "file" : originalName;
        String namePart = base;
        String extPart = "";
        int dot = base.lastIndexOf('.');
        if (dot > 0 && dot < base.length() - 1) {
            namePart = base.substring(0, dot);
            extPart = base.substring(dot);
        }
        String candidate = base;
        int index = 1;
        File file = new File(dir, candidate);
        while (file.exists() || !used.add(candidate)) {
            candidate = namePart + "(" + index + ")" + extPart;
            file = new File(dir, candidate);
            index++;
        }
        return file;
    }
}
