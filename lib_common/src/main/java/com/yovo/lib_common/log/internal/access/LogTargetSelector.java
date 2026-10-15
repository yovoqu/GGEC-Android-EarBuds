package com.yovo.lib_common.log.internal.access;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.YoFileManager;
import com.yovo.lib_common.system.file.internal.access.AccessEnvironment;
import com.yovo.lib_common.system.file.internal.access.AccessGate;
import com.yovo.lib_common.system.file.internal.access.AndroidAccessEnvironment;
import com.yovo.lib_common.system.file.model.AccessCheckResult;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.FileTarget;
import com.yovo.lib_common.log.model.FileLogReason;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.system.file.model.PathTarget;
import com.yovo.lib_common.log.YoLog;

import java.io.File;

/**
 * 日志文件目标选择：主目录 → fallback → logcat-only。
 */
public final class LogTargetSelector {

    private static final String TAG = "LogTargetSelector";

    private LogTargetSelector() {
    }

    @NonNull
    public static Selection select(@NonNull Context context, @NonNull LogConfig config) {
        if (!config.enableFile()) {
            YoLog.d(TAG, "配置已禁用文件日志");
            return Selection.none(FileLogReason.NOT_REQUESTED, "enableFile=false");
        }
        AccessEnvironment environment = new AndroidAccessEnvironment(context);

        if (config.fileTarget() != null) {
            Selection primary = tryTarget(
                    context, config, config.fileTarget(), environment, false);
            if (primary.target() != null) {
                YoLog.d(TAG, "已选择主日志目录: " + primary.target());
                return primary;
            }
            if (config.fallbackFileTarget() != null) {
                Selection fallback = tryTarget(
                        context,
                        config,
                        config.fallbackFileTarget(),
                        environment,
                        true);
                if (fallback.target() != null) {
                    YoLog.w(TAG, "主日志目录不可用，改用备用目录: "
                            + fallback.target() + " (" + primary.detail() + ")");
                    return fallback;
                }
                return Selection.none(
                        fallback.reason(),
                        "primary: " + primary.detail() + "; fallback: " + fallback.detail());
            }
            return primary;
        }
        if (config.fallbackFileTarget() != null) {
            Selection selection = tryTarget(
                    context, config, config.fallbackFileTarget(), environment, false);
            if (selection.target() != null) {
                YoLog.d(TAG, "已选择备用日志目录: " + selection.target());
            } else {
                YoLog.w(TAG, "日志目录不可用: " + selection.reason()
                        + " (" + selection.detail() + ")");
            }
            return selection;
        }
        YoLog.w(TAG, "未配置日志目录");
        return Selection.none(FileLogReason.NOT_REQUESTED, "no file target configured");
    }

    @NonNull
    private static Selection tryTarget(@NonNull Context context,
                                       @NonNull LogConfig config,
                                       @NonNull PathTarget target,
                                       @NonNull AccessEnvironment environment,
                                       boolean fallback) {
        AccessCheckResult gate =
                AccessGate.checkPath(target, environment);
        if (!gate.isAllowed()) {
            return Selection.none(
                    FileLogReason.DENIED, "access denied: " + gate.reason());
        }
        FileResult<File> dir = YoFileManager.ensureDir(context, target);
        if (!dir.success()) {
            return Selection.none(FileLogReason.IO_ERROR, dir.requireFailure().message());
        }
        FileResult<Long> space =
                YoFileManager.usableSpace(context, FileTarget.path(target));
        if (!space.success()) {
            return Selection.none(FileLogReason.IO_ERROR, space.requireFailure().message());
        }
        if (space.value() < config.minUsableSpaceBytes()) {
            return Selection.none(
                    FileLogReason.LOW_DISK,
                    "usable space below threshold: " + space.value());
        }
        return Selection.target(target, fallback, null);
    }

    public static final class Selection {

        @Nullable
        private final PathTarget target;

        private final boolean usingFallback;

        @NonNull
        private final FileLogReason reason;

        @Nullable
        private final String detail;

        private Selection(@Nullable PathTarget target,
                          boolean usingFallback,
                          @NonNull FileLogReason reason,
                          @Nullable String detail) {
            this.target = target;
            this.usingFallback = usingFallback;
            this.reason = reason;
            this.detail = detail;
        }

        @NonNull
        private static Selection target(@NonNull PathTarget target,
                                        boolean usingFallback,
                                        @Nullable String detail) {
            return new Selection(target, usingFallback, FileLogReason.NOT_REQUESTED, detail);
        }

        @NonNull
        private static Selection none(@NonNull FileLogReason reason,
                                      @Nullable String detail) {
            return new Selection(null, false, reason, detail);
        }

        @Nullable
        public PathTarget target() {
            return target;
        }

        public boolean usingFallback() {
            return usingFallback;
        }

        @NonNull
        public FileLogReason reason() {
            return reason;
        }

        @Nullable
        public String detail() {
            return detail;
        }
    }
}
