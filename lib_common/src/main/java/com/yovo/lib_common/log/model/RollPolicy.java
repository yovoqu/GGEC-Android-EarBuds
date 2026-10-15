package com.yovo.lib_common.log.model;

/**
 * 单文件大小滚动策略。
 */
public final class RollPolicy {

    private final long maxFileBytes;

    private final int maxBackupIndex;

    public RollPolicy(long maxFileBytes, int maxBackupIndex) {
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }
        if (maxBackupIndex < 0) {
            throw new IllegalArgumentException("maxBackupIndex must be >= 0");
        }
        this.maxFileBytes = maxFileBytes;
        this.maxBackupIndex = maxBackupIndex;
    }

    public long maxFileBytes() {
        return maxFileBytes;
    }

    public int maxBackupIndex() {
        return maxBackupIndex;
    }
}
