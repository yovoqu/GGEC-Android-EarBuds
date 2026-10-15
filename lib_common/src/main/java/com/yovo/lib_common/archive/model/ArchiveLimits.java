package com.yovo.lib_common.archive.model;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 解压资源上限，用于缓解 Zip Bomb。统计的是实际写出字节，而非条目声明大小。
 */
public final class ArchiveLimits {

    private static final int DEFAULT_MAX_ENTRIES = 512;
    private static final long DEFAULT_MAX_ENTRY_BYTES = 200L * 1024L * 1024L;
    private static final long DEFAULT_MAX_TOTAL_BYTES = 500L * 1024L * 1024L;

    private final int maxEntries;
    private final long maxEntryBytes;
    private final long maxTotalBytes;

    private ArchiveLimits(int maxEntries, long maxEntryBytes, long maxTotalBytes) {
        this.maxEntries = maxEntries;
        this.maxEntryBytes = maxEntryBytes;
        this.maxTotalBytes = maxTotalBytes;
    }

    @NonNull
    public static ArchiveLimits defaults() {
        return new ArchiveLimits(
                DEFAULT_MAX_ENTRIES, DEFAULT_MAX_ENTRY_BYTES, DEFAULT_MAX_TOTAL_BYTES);
    }

    @NonNull
    public static ArchiveLimits of(int maxEntries, long maxEntryBytes, long maxTotalBytes) {
        if (maxEntries <= 0 || maxEntryBytes <= 0L || maxTotalBytes <= 0L) {
            throw new IllegalArgumentException("limits must be positive");
        }
        return new ArchiveLimits(maxEntries, maxEntryBytes, maxTotalBytes);
    }

    public int maxEntries() {
        return maxEntries;
    }

    public long maxEntryBytes() {
        return maxEntryBytes;
    }

    public long maxTotalBytes() {
        return maxTotalBytes;
    }
}
