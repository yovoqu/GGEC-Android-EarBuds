package com.yovo.lib_common.log.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.PathTarget;

/**
 * 文件日志状态（可查询）。
 */
public final class FileLogStatus {

    private final boolean enabled;

    private final boolean degraded;

    @Nullable
    private final PathTarget activeTarget;

    @Nullable
    private final FileLogReason reason;

    @Nullable
    private final String detail;

    private FileLogStatus(boolean enabled,
                          boolean degraded,
                          @Nullable PathTarget activeTarget,
                          @Nullable FileLogReason reason,
                          @Nullable String detail) {
        this.enabled = enabled;
        this.degraded = degraded;
        this.activeTarget = activeTarget;
        this.reason = reason;
        this.detail = detail;
    }

    @NonNull
    public static FileLogStatus enabled(@NonNull PathTarget target) {
        return new FileLogStatus(true, false, target, null, null);
    }

    /** 文件日志仍启用，但主目录不可用、正在使用回退目录。 */
    @NonNull
    public static FileLogStatus enabledDegraded(@NonNull FileLogReason reason,
                                                @NonNull PathTarget activeTarget,
                                                @Nullable String detail) {
        return new FileLogStatus(true, true, activeTarget, reason, detail);
    }

    @NonNull
    public static FileLogStatus degraded(@NonNull FileLogReason reason,
                                         @Nullable PathTarget activeTarget,
                                         @Nullable String detail) {
        return new FileLogStatus(false, true, activeTarget, reason, detail);
    }

    @NonNull
    public static FileLogStatus disabled(@NonNull FileLogReason reason,
                                         @Nullable String detail) {
        return new FileLogStatus(false, false, null, reason, detail);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean degraded() {
        return degraded;
    }

    @Nullable
    public PathTarget activeTarget() {
        return activeTarget;
    }

    @Nullable
    public FileLogReason reason() {
        return reason;
    }

    @Nullable
    public String detail() {
        return detail;
    }
}
