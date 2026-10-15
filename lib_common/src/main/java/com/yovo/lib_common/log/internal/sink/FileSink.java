package com.yovo.lib_common.log.internal.sink;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.internal.format.LogFormatter;
import com.yovo.lib_common.log.internal.policy.RetentionController;
import com.yovo.lib_common.log.internal.policy.RollController;
import com.yovo.lib_common.log.model.FileLogReason;
import com.yovo.lib_common.log.model.FileLogStatus;
import com.yovo.lib_common.log.model.FileNamePolicy;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogRecord;
import com.yovo.lib_common.log.model.LogSink;
import com.yovo.lib_common.system.file.YoFileManager;
import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.FileEntry;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.FileTarget;
import com.yovo.lib_common.system.file.model.OpenOptions;
import com.yovo.lib_common.system.file.model.PathTarget;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件 Sink：经 YoFileManager 追加/滚动/保留；失败降级 logcat-only。
 */
public final class FileSink implements LogSink {

    private static final int SPACE_CHECK_INTERVAL = 256;

    private final Context appContext;

    @NonNull
    private final PathTarget activeTarget;

    @NonNull
    private final LogConfig config;

    @NonNull
    private final String processSuffix;

    @NonNull
    private final LogFormatter formatter;

    @NonNull
    private final RollController rollController;

    @Nullable
    private File activeFile;

    @Nullable
    private OutputStream stream;

    private long currentSize;

    private int writeCount;

    private final boolean usingFallback;

    private volatile boolean degraded;

    @NonNull
    private volatile FileLogReason reason = FileLogReason.NOT_REQUESTED;

    @Nullable
    private volatile String detail;

    public FileSink(@NonNull Context context,
                    @NonNull PathTarget activeTarget,
                    @NonNull LogConfig config,
                    @NonNull String processSuffix,
                    boolean usingFallback) {
        appContext = context.getApplicationContext();
        this.activeTarget = activeTarget;
        this.config = config;
        this.processSuffix = processSuffix;
        this.usingFallback = usingFallback;
        formatter = new LogFormatter();
        rollController = new RollController(
                config.rollPolicy(), config.fileNamePolicy(), processSuffix);
    }

    /** 打开活动文件并执行首次保留清理；失败进入降级状态。 */
    public void init() {
        FileResult<File> dir = YoFileManager.ensureDir(appContext, activeTarget);
        if (!dir.success()) {
            degrade(FileLogReason.IO_ERROR, dir.requireFailure().message());
            return;
        }
        activeFile = new File(
                dir.value(),
                config.fileNamePolicy().nameFor(System.currentTimeMillis(), processSuffix));
        currentSize = activeFile.length();
        openActive();
        if (!degraded) {
            runRetention();
        }
    }

    @Override
    public void write(@NonNull LogRecord record) {
        if (degraded) {
            return;
        }
        LogLevel min = config.fileMinLevel() != null
                ? config.fileMinLevel()
                : config.minLevel();
        if (!record.level().isEnabled(min)) {
            return;
        }
        if (++writeCount % SPACE_CHECK_INTERVAL == 0 && !hasEnoughSpace()) {
            return;
        }
        if (rollController.shouldSwitchDay(
                activeFile == null ? "" : activeFile.getName(),
                record.timestampMillis())) {
            switchDaily(record.timestampMillis());
        }
        if (rollController.shouldRoll(currentSize)) {
            roll();
        }
        if (degraded || stream == null || activeFile == null) {
            return;
        }
        String line = formatter.format(
                record, config.captureThread(), config.captureContext());
        if (record.throwable() != null) {
            line += "\n" + formatter.throwableText(record.throwable());
        }
        line += "\n";
        try {
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            stream.write(bytes);
            currentSize += bytes.length;
            if (config.urgentFlushLevels().contains(record.level())) {
                stream.flush();
            }
        } catch (IOException e) {
            degrade(FileLogReason.IO_ERROR, "write failed: " + e.getMessage());
        }
    }

    @Override
    public void flush() {
        OutputStream local = stream;
        if (local != null) {
            try {
                local.flush();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() {
        closeStream();
    }

    @NonNull
    public FileLogStatus status() {
        if (degraded) {
            return FileLogStatus.degraded(reason, activeTarget, detail);
        }
        if (usingFallback) {
            return FileLogStatus.enabledDegraded(
                    FileLogReason.PRIMARY_UNAVAILABLE_USING_FALLBACK,
                    activeTarget,
                    "primary file target unavailable, using fallback");
        }
        return FileLogStatus.enabled(activeTarget);
    }

    private void switchDaily(long now) {
        if (!hasEnoughSpace()) {
            return;
        }
        closeStream();
        if (activeFile == null || activeFile.getParentFile() == null) {
            return;
        }
        activeFile = new File(
                activeFile.getParentFile(),
                config.fileNamePolicy().nameFor(now, processSuffix));
        currentSize = activeFile.length();
        openActive();
        if (!degraded) {
            runRetention();
        }
    }

    private void roll() {
        if (!hasEnoughSpace()) {
            return;
        }
        if (activeFile == null) {
            return;
        }
        closeStream();
        File dir = activeFile.getParentFile();
        List<File> chain = rollController.backupChain(activeFile);
        for (int i = chain.size() - 1; i >= 1; i--) {
            File target = chain.get(i);
            File source = chain.get(i - 1);
            deleteQuietly(target);
            if (source.exists() && !moveQuietly(source, target)) {
                degrade(FileLogReason.IO_ERROR, "roll shift failed: " + source.getName());
                return;
            }
        }
        if (!chain.isEmpty()) {
            File first = chain.get(0);
            deleteQuietly(first);
            if (!moveQuietly(activeFile, first)) {
                degrade(FileLogReason.IO_ERROR, "roll failed: " + activeFile.getName());
                return;
            }
        }
        currentSize = 0L;
        openActive();
        if (!degraded) {
            runRetention();
        }
    }

    private void openActive() {
        if (activeFile == null || activeFile.getParentFile() == null) {
            degrade(FileLogReason.IO_ERROR, "active file has no parent");
            return;
        }
        FileResult<OutputStream> opened = YoFileManager.openOutputStream(
                appContext,
                FileTarget.path(PathTarget.custom(
                        activeFile.getParentFile(), activeFile.getName())),
                OpenOptions.WRITE,
                OpenOptions.CREATE,
                OpenOptions.APPEND);
        if (!opened.success()) {
            degrade(FileLogReason.IO_ERROR, opened.requireFailure().message());
            return;
        }
        stream = opened.value();
    }

    private void runRetention() {
        if (activeFile == null || activeFile.getParentFile() == null) {
            return;
        }
        File dir = activeFile.getParentFile();
        FileResult<List<FileEntry>> listed = YoFileManager.list(
                appContext,
                FileTarget.path(PathTarget.custom(dir, "")),
                entry -> config.fileNamePolicy().matches(entry.name(), processSuffix));
        if (!listed.success()) {
            return;
        }
        List<File> files = new ArrayList<>();
        for (FileEntry entry : listed.value()) {
            if (entry.path() != null) {
                files.add(new File(entry.path()));
            }
        }
        List<File> toDelete = RetentionController.selectForDeletion(
                files, config.retentionPolicy(), System.currentTimeMillis());
        for (File file : toDelete) {
            if (activeFile != null
                    && file.getAbsolutePath().equals(activeFile.getAbsolutePath())) {
                continue;
            }
            File parent = file.getParentFile();
            if (parent != null) {
                YoFileManager.delete(
                        appContext,
                        FileTarget.path(PathTarget.custom(parent, file.getName())),
                        false);
            }
        }
    }

    /** 写前/滚动前磁盘空间检查；不足时降级 logcat-only 并返回 false。 */
    private boolean hasEnoughSpace() {
        FileResult<Long> space = YoFileManager.usableSpace(
                appContext, FileTarget.path(activeTarget));
        if (!space.success()) {
            degrade(FileLogReason.IO_ERROR, space.requireFailure().message());
            return false;
        }
        if (space.value() < config.minUsableSpaceBytes()) {
            degrade(
                    FileLogReason.LOW_DISK,
                    "usable space below threshold: " + space.value());
            return false;
        }
        return true;
    }

    private boolean moveQuietly(@NonNull File source, @NonNull File target) {
        File parent = target.getParentFile();
        if (parent == null) {
            return false;
        }
        FileResult<Void> moved = YoFileManager.move(
                appContext,
                FileTarget.path(PathTarget.custom(parent, source.getName())),
                FileTarget.path(PathTarget.custom(parent, target.getName())),
                ConflictPolicy.OVERWRITE);
        return moved.success();
    }

    private void deleteQuietly(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        YoFileManager.delete(
                appContext,
                FileTarget.path(PathTarget.custom(parent, file.getName())),
                false);
    }

    private void closeStream() {
        OutputStream local = stream;
        stream = null;
        if (local != null) {
            try {
                local.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void degrade(@NonNull FileLogReason degradeReason,
                         @Nullable String degradeDetail) {
        degraded = true;
        reason = degradeReason;
        detail = degradeDetail;
        closeStream();
    }
}
