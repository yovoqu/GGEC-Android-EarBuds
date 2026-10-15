package com.yovo.lib_common.log.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.PathTarget;

/**
 * YoLog.init 结果。
 */
public final class InitResult {

    private final boolean logcatEnabled;

    private final boolean fileEnabled;

    @Nullable
    private final PathTarget activeFileTarget;

    @Nullable
    private final String fileDisabledReason;

    public InitResult(boolean logcatEnabled,
                      boolean fileEnabled,
                      @Nullable PathTarget activeFileTarget,
                      @Nullable String fileDisabledReason) {
        this.logcatEnabled = logcatEnabled;
        this.fileEnabled = fileEnabled;
        this.activeFileTarget = activeFileTarget;
        this.fileDisabledReason = fileDisabledReason;
    }

    public boolean logcatEnabled() {
        return logcatEnabled;
    }

    public boolean fileEnabled() {
        return fileEnabled;
    }

    @Nullable
    public PathTarget activeFileTarget() {
        return activeFileTarget;
    }

    @Nullable
    public String fileDisabledReason() {
        return fileDisabledReason;
    }
}
