package com.yovo.lib_common.archive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.archive.internal.core.ArchiveCore;
import com.yovo.lib_common.archive.model.ArchiveEntry;
import com.yovo.lib_common.archive.model.ArchiveEntryFilter;
import com.yovo.lib_common.archive.model.ArchiveFormat;
import com.yovo.lib_common.archive.model.ArchiveLimits;
import com.yovo.lib_common.archive.model.ArchivePackResult;
import com.yovo.lib_common.archive.model.ArchiveUnpackResult;
import com.yovo.lib_common.archive.model.UnpackLayout;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * 归档域对外静态门面，只承载契约，不包含实现。
 */
public final class YoArchive {

    private YoArchive() {
    }

    @NonNull
    public static PackBuilder pack() {
        return ArchiveCore.pack();
    }

    @NonNull
    public static UnpackBuilder unpack() {
        return ArchiveCore.unpack();
    }

    @NonNull
    public static ListBuilder list() {
        return ArchiveCore.list();
    }

    public interface PackBuilder {
        @NonNull
        PackBuilder sources(@Nullable List<File> files);

        @NonNull
        PackBuilder source(@Nullable File file);

        @NonNull
        PackBuilder destination(@NonNull File file);

        @NonNull
        PackBuilder format(@NonNull ArchiveFormat format);

        @NonNull
        ArchivePackResult execute();
    }

    public interface UnpackBuilder {
        @NonNull
        UnpackBuilder archive(@NonNull File file);

        @NonNull
        UnpackBuilder inputStream(@NonNull InputStream in);

        @NonNull
        UnpackBuilder destination(@NonNull File dir);

        @NonNull
        UnpackBuilder layout(@NonNull UnpackLayout layout);

        @NonNull
        UnpackBuilder filter(@Nullable ArchiveEntryFilter filter);

        @NonNull
        UnpackBuilder limits(@NonNull ArchiveLimits limits);

        @NonNull
        UnpackBuilder format(@NonNull ArchiveFormat format);

        @NonNull
        ArchiveUnpackResult execute();
    }

    public interface ListBuilder {
        @NonNull
        ListBuilder archive(@NonNull File file);

        @NonNull
        ListBuilder inputStream(@NonNull InputStream in);

        @NonNull
        ListBuilder format(@NonNull ArchiveFormat format);

        @NonNull
        List<ArchiveEntry> execute();
    }
}
