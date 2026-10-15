package com.yovo.lib_common.archive.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.archive.YoArchive;
import com.yovo.lib_common.archive.internal.engine.ArchiveEngine;
import com.yovo.lib_common.archive.model.ArchiveEntry;
import com.yovo.lib_common.archive.model.ArchiveEntryFilter;
import com.yovo.lib_common.archive.model.ArchiveFormat;
import com.yovo.lib_common.archive.model.ArchiveLimits;
import com.yovo.lib_common.archive.model.ArchivePackResult;
import com.yovo.lib_common.archive.model.ArchiveUnpackResult;
import com.yovo.lib_common.archive.model.UnpackLayout;
import com.yovo.lib_common.log.YoLog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 归档域内部编排核心。
 */
public final class ArchiveCore {

    private static final String TAG = "ArchiveCore";

    private ArchiveCore() {
    }

    @NonNull
    public static YoArchive.PackBuilder pack() {
        return new PackBuilderImpl();
    }

    @NonNull
    public static YoArchive.UnpackBuilder unpack() {
        return new UnpackBuilderImpl();
    }

    @NonNull
    public static YoArchive.ListBuilder list() {
        return new ListBuilderImpl();
    }

    public static final class PackBuilderImpl implements YoArchive.PackBuilder {
        private final List<File> sources = new ArrayList<>();
        private File destination;
        private ArchiveFormat format = ArchiveFormat.ZIP;

        private PackBuilderImpl() {
        }

        @NonNull
        @Override
        public YoArchive.PackBuilder sources(@Nullable List<File> files) {
            if (files != null) {
                sources.addAll(files);
            }
            return this;
        }

        @NonNull
        @Override
        public YoArchive.PackBuilder source(@Nullable File file) {
            if (file != null) {
                sources.add(file);
            }
            return this;
        }

        @NonNull
        @Override
        public YoArchive.PackBuilder destination(@NonNull File file) {
            this.destination = file;
            return this;
        }

        @NonNull
        @Override
        public YoArchive.PackBuilder format(@NonNull ArchiveFormat format) {
            this.format = format;
            return this;
        }

        @NonNull
        @Override
        public ArchivePackResult execute() {
            if (destination == null) {
                return ArchivePackResult.ioError("destination is null");
            }
            YoLog.d(TAG, "开始打包：源文件数=" + sources.size()
                    + "，目标=" + destination);
            List<File> valid = new ArrayList<>();
            for (File file : sources) {
                if (file != null && file.isFile() && file.length() > 0) {
                    valid.add(file);
                }
            }
            if (valid.isEmpty()) {
                return ArchivePackResult.noSources();
            }
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return ArchivePackResult.ioError("cannot create parent dir");
            }
            ArchiveEngine engine = ArchiveEngines.forFormat(format);
            if (engine == null) {
                return ArchivePackResult.ioError("unsupported format: " + format);
            }
            try (FileOutputStream fos = new FileOutputStream(destination);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                int count = engine.pack(valid, bos);
                if (count <= 0) {
                    //noinspection ResultOfMethodCallIgnored
                    destination.delete();
                    return ArchivePackResult.noSources();
                }
                return ArchivePackResult.success(destination, count);
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                destination.delete();
                YoLog.e(TAG, "打包失败: " + destination, e);
                return ArchivePackResult.ioError(e.getMessage());
            }
        }
    }

    public static final class UnpackBuilderImpl implements YoArchive.UnpackBuilder {
        private File archiveFile;
        private InputStream inputStream;
        private File destination;
        private UnpackLayout layout = UnpackLayout.FLATTEN;
        private ArchiveEntryFilter filter = ArchiveEntryFilter.ACCEPT_ALL;
        private ArchiveLimits limits = ArchiveLimits.defaults();
        private ArchiveFormat format = ArchiveFormat.ZIP;

        private UnpackBuilderImpl() {
        }

        @NonNull
        @Override
        public YoArchive.UnpackBuilder archive(@NonNull File file) {
            this.archiveFile = file;
            return this;
        }

        @NonNull
        @Override
        public YoArchive.UnpackBuilder inputStream(@NonNull InputStream in) {
            this.inputStream = in;
            return this;
        }

        @NonNull
        @Override
        public YoArchive.UnpackBuilder destination(@NonNull File dir) {
            this.destination = dir;
            return this;
        }

        @NonNull
        @Override
        public YoArchive.UnpackBuilder layout(@NonNull UnpackLayout layout) {
            this.layout = layout;
            return this;
        }

        @NonNull
        @Override
        public YoArchive.UnpackBuilder filter(@Nullable ArchiveEntryFilter filter) {
            this.filter = filter != null ? filter : ArchiveEntryFilter.ACCEPT_ALL;
            return this;
        }

        @NonNull
        @Override
        public YoArchive.UnpackBuilder limits(@NonNull ArchiveLimits limits) {
            this.limits = limits;
            return this;
        }

        @NonNull
        @Override
        public YoArchive.UnpackBuilder format(@NonNull ArchiveFormat format) {
            this.format = format;
            return this;
        }

        @NonNull
        @Override
        public ArchiveUnpackResult execute() {
            if (destination == null) {
                return ArchiveUnpackResult.failed("destination is null");
            }
            YoLog.d(TAG, "开始解压：归档="
                    + (archiveFile != null ? archiveFile : inputStream)
                    + "，目标=" + destination);
            ArchiveEngine engine = ArchiveEngines.forFormat(format);
            if (engine == null) {
                return ArchiveUnpackResult.failed("unsupported format: " + format);
            }
            try {
                if (inputStream != null) {
                    return engine.unpack(inputStream, destination, layout, filter, limits);
                }
                if (archiveFile == null || !archiveFile.isFile()) {
                    return ArchiveUnpackResult.failed("archive file missing");
                }
                try (InputStream in = new BufferedInputStream(new FileInputStream(archiveFile))) {
                    return engine.unpack(in, destination, layout, filter, limits);
                }
            } catch (Exception e) {
                YoLog.e(TAG, "解压失败: " + destination, e);
                return ArchiveUnpackResult.failed(e.getMessage());
            }
        }
    }

    public static final class ListBuilderImpl implements YoArchive.ListBuilder {
        private File archiveFile;
        private InputStream inputStream;
        private ArchiveFormat format = ArchiveFormat.ZIP;

        private ListBuilderImpl() {
        }

        @NonNull
        @Override
        public YoArchive.ListBuilder archive(@NonNull File file) {
            this.archiveFile = file;
            return this;
        }

        @NonNull
        @Override
        public YoArchive.ListBuilder inputStream(@NonNull InputStream in) {
            this.inputStream = in;
            return this;
        }

        @NonNull
        @Override
        public YoArchive.ListBuilder format(@NonNull ArchiveFormat format) {
            this.format = format;
            return this;
        }

        @NonNull
        @Override
        public List<ArchiveEntry> execute() {
            ArchiveEngine engine = ArchiveEngines.forFormat(format);
            if (engine == null) {
                return Collections.emptyList();
            }
            try {
                if (inputStream != null) {
                    return engine.list(inputStream);
                }
                if (archiveFile == null || !archiveFile.isFile()) {
                    return Collections.emptyList();
                }
                try (InputStream in = new BufferedInputStream(new FileInputStream(archiveFile))) {
                    return engine.list(in);
                }
            } catch (Exception e) {
                YoLog.w(TAG, "列出归档内容失败", e);
                return Collections.emptyList();
            }
        }
    }
}
