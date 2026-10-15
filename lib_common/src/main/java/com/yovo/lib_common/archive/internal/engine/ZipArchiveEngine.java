package com.yovo.lib_common.archive.internal.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.archive.internal.engine.ArchiveEngine;
import com.yovo.lib_common.archive.model.ArchiveEntry;
import com.yovo.lib_common.archive.model.ArchiveEntryFilter;
import com.yovo.lib_common.archive.model.ArchiveFormat;
import com.yovo.lib_common.archive.model.ArchiveLimits;
import com.yovo.lib_common.archive.model.ArchiveUnpackResult;
import com.yovo.lib_common.archive.model.UnpackLayout;
import com.yovo.lib_common.archive.internal.security.BombGuard;
import com.yovo.lib_common.archive.internal.security.PathSafety;
import com.yovo.lib_common.archive.internal.security.UniqueFileNames;
import com.yovo.lib_common.log.YoLog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description ZIP 格式归档引擎。解压在单次 {@link ZipInputStream} 循环内完成安全校验。
 */
public final class ZipArchiveEngine implements ArchiveEngine {

    private static final String TAG = "ZipArchiveEngine";

    public static final ZipArchiveEngine INSTANCE = new ZipArchiveEngine();

    private static final int BUFFER_SIZE = 8 * 1024;

    private ZipArchiveEngine() {
    }

    @NonNull
    @Override
    public ArchiveFormat format() {
        return ArchiveFormat.ZIP;
    }

    @Override
    public int pack(@NonNull List<File> sources, @NonNull OutputStream out) throws Exception {
        Set<String> usedNames = new HashSet<>();
        int count = 0;
        ZipOutputStream zos = new ZipOutputStream(out);
        byte[] buffer = new byte[BUFFER_SIZE];
        for (File source : sources) {
            if (source == null || !source.isFile() || source.length() <= 0) {
                continue;
            }
            String entryName = uniqueEntryName(usedNames, source.getName());
            try (InputStream in = new BufferedInputStream(new FileInputStream(source))) {
                zos.putNextEntry(new ZipEntry(entryName));
                int read;
                while ((read = in.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                }
                zos.closeEntry();
                count++;
            }
        }
        zos.finish();
        YoLog.d(TAG, "已打包条目数: " + count);
        return count;
    }

    @NonNull
    @Override
    public List<ArchiveEntry> list(@NonNull InputStream in) throws Exception {
        List<ArchiveEntry> entries = new ArrayList<>();
        ZipInputStream zis = new ZipInputStream(in);
        try {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                entries.add(new ArchiveEntry(
                        zipEntry.getName(),
                        zipEntry.getSize(),
                        zipEntry.isDirectory()));
                zis.closeEntry();
            }
        } finally {
            // 不关闭调用方传入的流；由调用方决定生命周期。
            try {
                zis.closeEntry();
            } catch (Exception ignored) {
            }
        }
        return entries;
    }

    @NonNull
    @Override
    public ArchiveUnpackResult unpack(@NonNull InputStream in,
                                      @NonNull File destinationDir,
                                      @NonNull UnpackLayout layout,
                                      @Nullable ArchiveEntryFilter filter,
                                      @NonNull ArchiveLimits limits) throws Exception {
        ArchiveEntryFilter effectiveFilter =
                filter != null ? filter : ArchiveEntryFilter.ACCEPT_ALL;
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            return ArchiveUnpackResult.failed("cannot create destination dir");
        }

        List<File> extracted = new ArrayList<>();
        int skipped = 0;
        int rejected = 0;
        int failed = 0;
        String message = null;
        BombGuard bomb = new BombGuard(limits);
        UniqueFileNames names = new UniqueFileNames();

        ZipInputStream zis = new ZipInputStream(in);
        try {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                File outFile = null;
                try {
                    bomb.onEntryStart();
                    ArchiveEntry entry = new ArchiveEntry(
                            zipEntry.getName(),
                            zipEntry.getSize(),
                            zipEntry.isDirectory());

                if (entry.isDirectory()) {
                    YoLog.v(TAG, "跳过目录条目: " + entry.name());
                    skipped++;
                    continue;
                }

                    File resolved = PathSafety.resolveTarget(
                            destinationDir, entry.name(), layout);
                    if (resolved == null) {
                        YoLog.w(TAG, "已拒绝不安全的条目: " + entry.name());
                        rejected++;
                        continue;
                    }

                    if (!effectiveFilter.accept(entry)) {
                        YoLog.v(TAG, "跳过被过滤的条目: " + entry.name());
                        skipped++;
                        continue;
                    }

                    outFile = names.allocate(
                            resolved.getParentFile() != null
                                    ? resolved.getParentFile()
                                    : destinationDir,
                            resolved.getName());
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        failed++;
                        message = "cannot create parent for " + outFile.getName();
                        YoLog.w(TAG, "无法为 " + outFile.getName() + " 创建父目录");
                        continue;
                    }

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        long written = bomb.copyBounded(zis, fos);
                        if (written <= 0L) {
                            //noinspection ResultOfMethodCallIgnored
                            outFile.delete();
                            failed++;
                            continue;
                        }
                        extracted.add(outFile);
                    }
                } catch (Exception e) {
                    if (outFile != null && outFile.isFile()) {
                        //noinspection ResultOfMethodCallIgnored
                        outFile.delete();
                    }
                    failed++;
                    message = e.getMessage();
                    YoLog.w(TAG, "条目解压失败: " + zipEntry.getName(), e);
                    // 限额类错误终止整个解压
                    if (message != null && message.contains("exceeds limit")) {
                        YoLog.w(TAG, "解压被归档限制中止: " + message);
                        return ArchiveUnpackResult.of(
                                extracted, skipped, rejected, failed, message);
                    }
                } finally {
                    try {
                        zis.closeEntry();
                    } catch (Exception ignored) {
                    }
                }
            }
        } finally {
            // 不关闭调用方传入的流；由调用方决定生命周期。
            try {
                zis.closeEntry();
            } catch (Exception ignored) {
            }
        }

        return ArchiveUnpackResult.of(extracted, skipped, rejected, failed, message);
    }

    @NonNull
    private static String uniqueEntryName(@NonNull Set<String> usedNames,
                                          @Nullable String original) {
        String base = (original == null || original.isEmpty()) ? "file" : original;
        if (usedNames.add(base)) {
            return base;
        }
        String namePart = base;
        String extPart = "";
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            namePart = base.substring(0, dot);
            extPart = base.substring(dot);
        }
        int index = 1;
        String candidate;
        do {
            candidate = namePart + "(" + index + ")" + extPart;
            index++;
        } while (!usedNames.add(candidate));
        return candidate;
    }
}
