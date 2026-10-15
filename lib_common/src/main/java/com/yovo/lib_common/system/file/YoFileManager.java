package com.yovo.lib_common.system.file;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.internal.core.FileCore;
import com.yovo.lib_common.system.file.model.AccessCheckResult;
import com.yovo.lib_common.system.file.model.AccessMode;
import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.FileEntry;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.FileTarget;
import com.yovo.lib_common.system.file.model.OpenOptions;
import com.yovo.lib_common.system.file.model.PathTarget;
import com.yovo.lib_common.system.file.model.SafDirectory;
import com.yovo.lib_common.system.file.model.SafFile;
import com.yovo.lib_common.system.file.model.SafTreePicker;
import com.yovo.lib_common.system.file.model.SafTreeStore;
import com.yovo.lib_common.system.file.model.StreamWriter;
import com.yovo.lib_common.system.file.model.UriFileInfo;
import com.yovo.lib_common.system.file.model.UriTarget;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

/**
 * 库内唯一通用文件入口：Path / content URI / SAF 树的增删改查与基础 IO。
 *
 * <p>权限只检查、不请求；日志命名/滚动/保留策略不属于本模块。</p>
 */
public final class YoFileManager {

    /** 不限制读取/复制大小。 */
    public static final long NO_SIZE_LIMIT = FileCore.NO_SIZE_LIMIT;

    /** Path 写操作默认最低可用空间字节数。 */
    public static final long DEFAULT_MIN_USABLE_SPACE_BYTES =
            FileCore.DEFAULT_MIN_USABLE_SPACE_BYTES;

    private YoFileManager() {
    }

    // ---- 门禁与解析 ----

    @NonNull
    public static AccessCheckResult checkAccess(@NonNull Context context,
                                                @NonNull FileTarget target,
                                                @NonNull AccessMode mode) {
        return FileCore.checkAccess(context, target, mode);
    }

    @NonNull
    public static FileResult<File> resolvePath(@NonNull Context context,
                                               @NonNull PathTarget target) {
        return FileCore.resolvePath(context, target);
    }

    @NonNull
    public static FileResult<Long> usableSpace(@NonNull Context context,
                                               @NonNull FileTarget target) {
        return FileCore.usableSpace(context, target);
    }

    // ---- 打开选项 ----

    @NonNull
    public static FileResult<InputStream> openInputStream(
            @NonNull Context context,
            @NonNull FileTarget target,
            @NonNull OpenOptions... options) {
        return FileCore.openInputStream(context, target, options);
    }

    @NonNull
    public static FileResult<OutputStream> openOutputStream(
            @NonNull Context context,
            @NonNull FileTarget target,
            @NonNull OpenOptions... options) {
        return FileCore.openOutputStream(context, target, options);
    }

    // ---- 元数据与枚举 ----

    @NonNull
    public static FileResult<Boolean> exists(@NonNull Context context,
                                             @NonNull FileTarget target) {
        return FileCore.exists(context, target);
    }

    @NonNull
    public static FileResult<Boolean> isDirectory(@NonNull Context context,
                                                  @NonNull FileTarget target) {
        return FileCore.isDirectory(context, target);
    }

    @NonNull
    public static FileResult<Long> length(@NonNull Context context,
                                          @NonNull FileTarget target) {
        return FileCore.length(context, target);
    }

    @NonNull
    public static FileResult<Long> lastModified(@NonNull Context context,
                                                @NonNull FileTarget target) {
        return FileCore.lastModified(context, target);
    }

    @NonNull
    public static FileResult<Void> setLastModified(@NonNull Context context,
                                                   @NonNull PathTarget target,
                                                   long millis) {
        return FileCore.setLastModified(context, target, millis);
    }

    @NonNull
    public static FileResult<UriFileInfo> inspect(@NonNull Context context,
                                                  @NonNull UriTarget target) {
        return FileCore.inspect(context, target);
    }

    @NonNull
    public static FileResult<List<FileEntry>> list(@NonNull Context context,
                                                   @NonNull FileTarget target) {
        return FileCore.list(context, target);
    }

    @NonNull
    public static FileResult<List<FileEntry>> list(
            @NonNull Context context,
            @NonNull FileTarget target,
            @Nullable Predicate<FileEntry> filter) {
        return FileCore.list(context, target, filter);
    }

    @NonNull
    public static FileResult<List<FileEntry>> listRecursive(
            @NonNull Context context,
            @NonNull FileTarget target,
            @Nullable Predicate<FileEntry> filter) {
        return FileCore.listRecursive(context, target, filter);
    }

    // ---- 目录与删除 ----

    @NonNull
    public static FileResult<File> ensureDir(@NonNull Context context,
                                             @NonNull PathTarget target) {
        return FileCore.ensureDir(context, target);
    }

    @NonNull
    public static FileResult<File> mkdir(@NonNull Context context,
                                         @NonNull PathTarget target) {
        return FileCore.mkdir(context, target);
    }

    @NonNull
    public static FileResult<File> mkdirs(@NonNull Context context,
                                          @NonNull PathTarget target) {
        return FileCore.mkdirs(context, target);
    }

    @NonNull
    public static FileResult<Void> delete(@NonNull Context context,
                                          @NonNull FileTarget target,
                                          boolean recursive) {
        return FileCore.delete(context, target, recursive);
    }

    @NonNull
    public static FileResult<File> createNewFile(@NonNull Context context,
                                                 @NonNull PathTarget target,
                                                 @NonNull ConflictPolicy conflict) {
        return FileCore.createNewFile(context, target, conflict);
    }

    // ---- 读写 ----

    @NonNull
    public static FileResult<byte[]> readBytes(@NonNull Context context,
                                               @NonNull FileTarget target,
                                               long maxBytes) {
        return FileCore.readBytes(context, target, maxBytes);
    }

    @NonNull
    public static FileResult<String> readText(@NonNull Context context,
                                              @NonNull FileTarget target,
                                              @NonNull Charset charset,
                                              long maxBytes) {
        return FileCore.readText(context, target, charset, maxBytes);
    }

    @NonNull
    public static FileResult<String> readText(@NonNull Context context,
                                              @NonNull FileTarget target,
                                              long maxBytes) {
        return readText(context, target, StandardCharsets.UTF_8, maxBytes);
    }

    @NonNull
    public static FileResult<Void> writeBytes(@NonNull Context context,
                                              @NonNull FileTarget target,
                                              @NonNull byte[] bytes,
                                              @NonNull ConflictPolicy conflict) {
        return FileCore.writeBytes(context, target, bytes, conflict);
    }

    @NonNull
    public static FileResult<Void> writeText(@NonNull Context context,
                                             @NonNull FileTarget target,
                                             @NonNull String text,
                                             @NonNull ConflictPolicy conflict) {
        return writeText(context, target, text, StandardCharsets.UTF_8, conflict);
    }

    @NonNull
    public static FileResult<Void> writeText(@NonNull Context context,
                                             @NonNull FileTarget target,
                                             @NonNull String text,
                                             @NonNull Charset charset,
                                             @NonNull ConflictPolicy conflict) {
        return FileCore.writeText(context, target, text, charset, conflict);
    }

    @NonNull
    public static FileResult<Void> appendBytes(@NonNull Context context,
                                               @NonNull FileTarget target,
                                               @NonNull byte[] bytes) {
        return FileCore.appendBytes(context, target, bytes);
    }

    @NonNull
    public static FileResult<Void> appendText(@NonNull Context context,
                                              @NonNull FileTarget target,
                                              @NonNull String text) {
        return appendText(context, target, text, StandardCharsets.UTF_8);
    }

    @NonNull
    public static FileResult<Void> appendText(@NonNull Context context,
                                              @NonNull FileTarget target,
                                              @NonNull String text,
                                              @NonNull Charset charset) {
        return FileCore.appendText(context, target, text, charset);
    }

    @NonNull
    public static FileResult<Void> writeAtomic(@NonNull Context context,
                                               @NonNull PathTarget target,
                                               @NonNull byte[] bytes) {
        return FileCore.writeAtomic(context, target, bytes);
    }

    @NonNull
    public static FileResult<Void> writeAtomic(@NonNull Context context,
                                               @NonNull PathTarget target,
                                               @NonNull StreamWriter writer) {
        return FileCore.writeAtomic(context, target, writer);
    }

    @NonNull
    public static FileResult<Void> truncate(@NonNull Context context,
                                            @NonNull PathTarget target,
                                            long size) {
        return FileCore.truncate(context, target, size);
    }

    // ---- 复制 / 移动 ----

    @NonNull
    public static FileResult<Long> copy(@NonNull Context context,
                                        @NonNull FileTarget source,
                                        @NonNull FileTarget destination,
                                        @NonNull ConflictPolicy conflict,
                                        long maxBytes) {
        return FileCore.copy(context, source, destination, conflict, maxBytes);
    }

    @NonNull
    public static FileResult<Long> copy(@NonNull Context context,
                                        @NonNull FileTarget source,
                                        @NonNull FileTarget destination,
                                        @NonNull ConflictPolicy conflict) {
        return copy(context, source, destination, conflict, NO_SIZE_LIMIT);
    }

    @NonNull
    public static FileResult<Void> move(@NonNull Context context,
                                        @NonNull FileTarget source,
                                        @NonNull FileTarget destination,
                                        @NonNull ConflictPolicy conflict) {
        return FileCore.move(context, source, destination, conflict);
    }

    @NonNull
    public static FileResult<File> copyUriInto(@NonNull Context context,
                                               @NonNull UriTarget source,
                                               @NonNull PathTarget destinationDir,
                                               @Nullable String preferredName,
                                               long maxBytes,
                                               @NonNull ConflictPolicy conflict) {
        return FileCore.copyUriInto(
                context, source, destinationDir, preferredName, maxBytes, conflict);
    }

    // ---- SAF 专项 ----

    @NonNull
    public static SafTreeStore treeStore(@NonNull Context context) {
        return FileCore.treeStore(context);
    }

    @NonNull
    public static SafTreePicker treePicker(@NonNull Context context) {
        return FileCore.treePicker(context);
    }

    /** 只提供契约 Intent；startActivityForResult 由 APP UI 执行。 */
    @NonNull
    public static Intent createTreePickerIntent() {
        return FileCore.createTreePickerIntent();
    }

    @NonNull
    public static FileResult<Void> takePersistableTree(@NonNull Context context,
                                                       @NonNull Uri treeUri,
                                                       int modeFlags) {
        return FileCore.takePersistableTree(context, treeUri, modeFlags);
    }

    @NonNull
    public static FileResult<Void> releasePersistableTree(@NonNull Context context,
                                                          @NonNull Uri treeUri,
                                                          int modeFlags) {
        return FileCore.releasePersistableTree(context, treeUri, modeFlags);
    }

    @NonNull
    public static List<Uri> listPersistedTreeUris(@NonNull Context context) {
        return FileCore.listPersistedTreeUris(context);
    }

    public static boolean isTreeAccessible(@NonNull Context context,
                                           @Nullable Uri treeUri) {
        return FileCore.isTreeAccessible(context, treeUri);
    }

    @Nullable
    public static SafDirectory openTree(@NonNull Context context,
                                        @NonNull Uri treeUri) {
        return FileCore.openTree(context, treeUri);
    }

    @NonNull
    public static FileResult<SafFile> copyFileToTree(@NonNull Context context,
                                                     @NonNull Uri treeUri,
                                                     @NonNull File source,
                                                     @NonNull String name,
                                                     @NonNull String mimeType,
                                                     @NonNull ConflictPolicy conflict) {
        return FileCore.copyFileToTree(
                context, treeUri, source, name, mimeType, conflict);
    }

    @NonNull
    public static FileResult<SafFile> copyFileToTree(@NonNull Context context,
                                                     @NonNull Uri treeUri,
                                                     @NonNull PathTarget source,
                                                     @NonNull String name,
                                                     @NonNull String mimeType,
                                                     @NonNull ConflictPolicy conflict) {
        return FileCore.copyFileToTree(
                context, treeUri, source, name, mimeType, conflict);
    }
}
