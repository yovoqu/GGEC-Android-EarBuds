package com.yovo.lib_common.log.model;

import android.util.Log;

/**
 * 日志级别；rank 越大越严重。
 */
public enum LogLevel {
    VERBOSE(2, 0),
    DEBUG(3, 1),
    INFO(4, 2),
    WARN(5, 3),
    ERROR(6, 4),
    ASSERT(7, 5);

    private final int androidPriority;
    private final int rank;

    LogLevel(int androidPriority, int rank) {
        this.androidPriority = androidPriority;
        this.rank = rank;
    }

    /** android.util.Log 优先级。 */
    public int androidPriority() {
        return androidPriority;
    }

    public int rank() {
        return rank;
    }

    /** 是否达到最低级别（>= minLevel）。 */
    public boolean isEnabled(LogLevel minLevel) {
        return rank >= minLevel.rank;
    }

    public static LogLevel fromAndroidPriority(int priority) {
        for (LogLevel level : values()) {
            if (level.androidPriority == priority) {
                return level;
            }
        }
        return priority <= Log.DEBUG ? DEBUG : INFO;
    }
}
