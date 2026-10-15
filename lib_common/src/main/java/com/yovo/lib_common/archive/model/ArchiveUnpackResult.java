package com.yovo.lib_common.archive.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 解压结果。区分提取成功、过滤器跳过、安全拒绝与失败。
 */
public final class ArchiveUnpackResult {

    public enum Status {
        SUCCESS,
        PARTIAL,
        FAILED
    }

    private final Status status;
    private final List<File> extractedFiles;
    private final int skippedByFilter;
    private final int rejectedUnsafe;
    private final int failedCount;
    @Nullable
    private final String message;

    private ArchiveUnpackResult(@NonNull Status status,
                                @NonNull List<File> extractedFiles,
                                int skippedByFilter,
                                int rejectedUnsafe,
                                int failedCount,
                                @Nullable String message) {
        this.status = status;
        this.extractedFiles = Collections.unmodifiableList(new ArrayList<>(extractedFiles));
        this.skippedByFilter = skippedByFilter;
        this.rejectedUnsafe = rejectedUnsafe;
        this.failedCount = failedCount;
        this.message = message;
    }

    @NonNull
    public static ArchiveUnpackResult of(@NonNull List<File> extracted,
                                         int skippedByFilter,
                                         int rejectedUnsafe,
                                         int failedCount,
                                         @Nullable String message) {
        Status status;
        if (failedCount > 0 && extracted.isEmpty()) {
            status = Status.FAILED;
        } else if (failedCount > 0 || rejectedUnsafe > 0) {
            status = extracted.isEmpty() ? Status.FAILED : Status.PARTIAL;
        } else if (!extracted.isEmpty() || skippedByFilter > 0) {
            status = Status.SUCCESS;
        } else {
            status = Status.FAILED;
        }
        return new ArchiveUnpackResult(
                status, extracted, skippedByFilter, rejectedUnsafe, failedCount, message);
    }

    @NonNull
    public static ArchiveUnpackResult failed(@Nullable String message) {
        return new ArchiveUnpackResult(Status.FAILED, Collections.emptyList(), 0, 0, 1, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    @NonNull
    public Status status() {
        return status;
    }

    @NonNull
    public List<File> extractedFiles() {
        return extractedFiles;
    }

    public int skippedByFilter() {
        return skippedByFilter;
    }

    public int rejectedUnsafe() {
        return rejectedUnsafe;
    }

    public int failedCount() {
        return failedCount;
    }

    @Nullable
    public String message() {
        return message;
    }
}
