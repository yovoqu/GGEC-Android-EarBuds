package com.yovo.lib_common.archive.internal.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.archive.model.ArchiveEntry;
import com.yovo.lib_common.archive.model.ArchiveEntryFilter;
import com.yovo.lib_common.archive.model.ArchiveFormat;
import com.yovo.lib_common.archive.model.ArchiveLimits;
import com.yovo.lib_common.archive.model.ArchiveUnpackResult;
import com.yovo.lib_common.archive.model.UnpackLayout;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 归档引擎契约：流式 pack / list / unpack。安全校验在 unpack 单循环内完成。
 */
public interface ArchiveEngine {

    @NonNull
    ArchiveFormat format();

    /**
     * 将源文件打包写入输出流。调用方负责关闭 {@code out}。
     *
     * @return 成功写入的条目数；无有效源返回 0。
     */
    int pack(@NonNull List<File> sources, @NonNull OutputStream out) throws Exception;

    /**
     * 只读列出条目。结果仅供预览，不得作为解压安全边界。
     */
    @NonNull
    List<ArchiveEntry> list(@NonNull InputStream in) throws Exception;

    /**
     * 在同一次流遍历中完成 PathSafety、过滤器与 Bomb 限额校验并解压。
     */
    @NonNull
    ArchiveUnpackResult unpack(@NonNull InputStream in,
                               @NonNull File destinationDir,
                               @NonNull UnpackLayout layout,
                               @Nullable ArchiveEntryFilter filter,
                               @NonNull ArchiveLimits limits) throws Exception;
}
