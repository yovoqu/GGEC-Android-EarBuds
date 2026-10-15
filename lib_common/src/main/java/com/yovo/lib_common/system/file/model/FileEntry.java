package com.yovo.lib_common.system.file.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * 目录枚举结果条目。
 */
public final class FileEntry {

    @NonNull
    private final String name;

    @Nullable
    private final String path;

    @Nullable
    private final Uri uri;

    private final long length;

    private final long lastModified;

    private final boolean directory;

    public FileEntry(@NonNull String name,
                     @Nullable String path,
                     @Nullable Uri uri,
                     long length,
                     long lastModified,
                     boolean directory) {
        this.name = name;
        this.path = path;
        this.uri = uri;
        this.length = length;
        this.lastModified = lastModified;
        this.directory = directory;
    }

    @NonNull
    public static FileEntry of(@NonNull File file) {
        return new FileEntry(
                file.getName(),
                file.getAbsolutePath(),
                null,
                file.length(),
                file.lastModified(),
                file.isDirectory());
    }

    @NonNull
    public static FileEntry of(@NonNull Uri uri,
                               @NonNull String name,
                               long length,
                               long lastModified,
                               boolean directory) {
        return new FileEntry(name, null, uri, length, lastModified, directory);
    }

    @NonNull
    public String name() {
        return name;
    }

    @Nullable
    public String path() {
        return path;
    }

    @Nullable
    public Uri uri() {
        return uri;
    }

    public long length() {
        return length;
    }

    public long lastModified() {
        return lastModified;
    }

    public boolean isDirectory() {
        return directory;
    }
}
