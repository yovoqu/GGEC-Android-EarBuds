package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * SAF 文件条目（模块中立，不暴露 lib_common 类型）。
 */
public final class SafEntry {

    @NonNull
    private final String name;
    private final long lastModified;
    private final long length;
    @NonNull
    private final FileOps ops;

    public SafEntry(@NonNull String name,
                    long lastModified,
                    long length,
                    @NonNull FileOps ops) {
        this.name = name;
        this.lastModified = lastModified;
        this.length = length;
        this.ops = ops;
    }

    @NonNull
    public String name() {
        return name;
    }

    public long lastModified() {
        return lastModified;
    }

    public long length() {
        return length;
    }

    @Nullable
    public File copyTo(@NonNull File target) {
        return ops.copyTo(target);
    }

    public boolean delete() {
        return ops.delete();
    }

    public interface FileOps {
        @Nullable
        File copyTo(@NonNull File target);

        boolean delete();
    }
}
