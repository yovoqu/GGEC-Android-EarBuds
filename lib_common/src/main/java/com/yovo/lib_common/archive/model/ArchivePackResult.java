package com.yovo.lib_common.archive.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 打包结果。
 */
public final class ArchivePackResult {

    public enum Status {
        SUCCESS,
        NO_SOURCES,
        IO_ERROR
    }

    private final Status status;
    @Nullable
    private final File archiveFile;
    private final int entryCount;
    @Nullable
    private final String message;

    private ArchivePackResult(@NonNull Status status,
                              @Nullable File archiveFile,
                              int entryCount,
                              @Nullable String message) {
        this.status = status;
        this.archiveFile = archiveFile;
        this.entryCount = entryCount;
        this.message = message;
    }

    @NonNull
    public static ArchivePackResult success(@NonNull File archiveFile, int entryCount) {
        return new ArchivePackResult(Status.SUCCESS, archiveFile, entryCount, null);
    }

    @NonNull
    public static ArchivePackResult noSources() {
        return new ArchivePackResult(Status.NO_SOURCES, null, 0, "no valid sources");
    }

    @NonNull
    public static ArchivePackResult ioError(@Nullable String message) {
        return new ArchivePackResult(Status.IO_ERROR, null, 0, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    @NonNull
    public Status status() {
        return status;
    }

    @Nullable
    public File archiveFile() {
        return archiveFile;
    }

    public int entryCount() {
        return entryCount;
    }

    @Nullable
    public String message() {
        return message;
    }
}
