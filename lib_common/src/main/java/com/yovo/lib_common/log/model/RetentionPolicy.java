package com.yovo.lib_common.log.model;

/**
 * 日志保留策略：文件数 / 天数 / 总字节。
 */
public final class RetentionPolicy {

    private final int maxFiles;

    private final int maxDays;

    private final long maxTotalBytes;

    public RetentionPolicy(int maxFiles, int maxDays, long maxTotalBytes) {
        if (maxFiles < 0 || maxDays < 0 || maxTotalBytes < 0) {
            throw new IllegalArgumentException("retention values must be >= 0");
        }
        this.maxFiles = maxFiles;
        this.maxDays = maxDays;
        this.maxTotalBytes = maxTotalBytes;
    }

    /** 0 表示不限制文件数。 */
    public int maxFiles() {
        return maxFiles;
    }

    /** 0 表示不按天数清理。 */
    public int maxDays() {
        return maxDays;
    }

    /** 0 表示不限制总大小。 */
    public long maxTotalBytes() {
        return maxTotalBytes;
    }
}
