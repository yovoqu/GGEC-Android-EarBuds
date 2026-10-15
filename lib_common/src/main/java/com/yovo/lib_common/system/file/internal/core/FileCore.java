package com.yovo.lib_common.system.file.internal.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.internal.access.AccessEnvironment;
import com.yovo.lib_common.system.file.internal.access.AccessGate;
import com.yovo.lib_common.system.file.internal.access.AndroidAccessEnvironment;
import com.yovo.lib_common.system.file.internal.path.AtomicPathWriter;
import com.yovo.lib_common.system.file.internal.path.PathFileStore;
import com.yovo.lib_common.system.file.internal.resolve.AndroidPathRootProvider;
import com.yovo.lib_common.system.file.internal.resolve.PathResolver;
import com.yovo.lib_common.system.file.internal.saf.SafTreeOps;
import com.yovo.lib_common.system.file.internal.saf.android.AndroidSafTreePicker;
import com.yovo.lib_common.system.file.internal.saf.store.PrefsSafTreeStore;
import com.yovo.lib_common.system.file.internal.uri.UriFileStore;
import com.yovo.lib_common.system.file.model.AccessCheckResult;
import com.yovo.lib_common.system.file.model.AccessMode;
import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.FileEntry;
import com.yovo.lib_common.system.file.model.FileFailureCode;
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
import com.yovo.lib_common.system.file.model.UriKind;
import com.yovo.lib_common.system.file.model.UriTarget;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import com.yovo.lib_common.log.YoLog;

/**
 * 文件能力编排：门面委托、组合 Path/Uri/Saf Store，并执行访问门禁。
 */
public final class FileCore {

    public static final long NO_SIZE_LIMIT = -1L;

    /** Path 写操作默认最低可用空间（与 YoLog 默认对齐）。 */
    public static final long DEFAULT_MIN_USABLE_SPACE_BYTES = 5L * 1024 * 1024;

    private static final String TAG = "FileCore";

    private FileCore() {
    }

    @NonNull
    public static AccessCheckResult checkAccess(@NonNull Context context,
                                                @NonNull FileTarget target,
                                                @NonNull AccessMode mode) {
        AccessCheckResult result = AccessGate.check(target, mode, environment(context));
        if (!result.isAllowed()) {
            YoLog.w(TAG, "访问被拒绝：目标=" + target
                    + "，模式=" + mode + "，原因=" + result.reason());
        }
        return result;
    }

    @NonNull
    public static FileResult<File> resolvePath(@NonNull Context context,
                                               @NonNull PathTarget target) {
        return PathResolver.resolve(target, new AndroidPathRootProvider(context));
    }

    @NonNull
    public static FileResult<Long> usableSpace(@NonNull Context context,
                                               @NonNull FileTarget target) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isUri()) {
            return FileResult.failure(
                    FileFailureCode.UNSUPPORTED, "usableSpace is path-only");
        }
        FileResult<File> resolved = resolvePath(context, target.asPath());
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        return PathFileStore.usableSpace(resolved.value());
    }

    @NonNull
    public static FileResult<InputStream> openInputStream(
            @NonNull Context context,
            @NonNull FileTarget target,
            @NonNull OpenOptions... options) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isPath()) {
            FileResult<File> resolved = resolvePath(context, target.asPath());
            if (!resolved.success()) {
                return FileResult.failure(resolved.requireFailure());
            }
            return PathFileStore.openInputStream(resolved.value());
        }
        UriTarget uriTarget = target.asUri();
        if (uriTarget.uriKind() == UriKind.TREE) {
            if (uriTarget.relativePath() == null) {
                return FileResult.failure(
                        FileFailureCode.INVALID_TARGET, "tree stream requires relativePath");
            }
            return SafTreeOps.openInputStream(
                    context, uriTarget.uri(), uriTarget.relativePath());
        }
        return UriFileStore.openInputStream(context, uriTarget);
    }

    @NonNull
    public static FileResult<OutputStream> openOutputStream(
            @NonNull Context context,
            @NonNull FileTarget target,
            @NonNull OpenOptions... options) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        // 默认不隐式 TRUNCATE 覆盖：无选项时按“追加/创建”打开。
        Set<OpenOptions> optionSet = options.length == 0
                ? EnumSet.of(OpenOptions.WRITE, OpenOptions.CREATE, OpenOptions.APPEND)
                : EnumSet.of(options[0], options);
        if (target.isPath()) {
            FileResult<File> resolved = resolvePath(context, target.asPath());
            if (!resolved.success()) {
                return FileResult.failure(resolved.requireFailure());
            }
            FileResult<Void> space = ensureMinUsableSpace(resolved.value());
            if (space != null) {
                return FileResult.failure(space.requireFailure());
            }
            return PathFileStore.openOutputStream(resolved.value(), optionSet);
        }
        return UriFileStore.openOutputStream(context, target.asUri(), optionSet);
    }

    @NonNull
    public static FileResult<Boolean> exists(@NonNull Context context,
                                             @NonNull FileTarget target) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isPath()) {
            FileResult<File> resolved = resolvePath(context, target.asPath());
            if (!resolved.success()) {
                return FileResult.failure(resolved.requireFailure());
            }
            return PathFileStore.exists(resolved.value());
        }
        UriTarget uri = target.asUri();
        if (uri.uriKind() == UriKind.TREE) {
            return FileResult.ok(
                    SafTreeOps.pathExists(context, uri.uri(), uri.relativePath()));
        }
        FileResult<UriFileInfo> info = UriFileStore.inspect(context, uri);
        return FileResult.ok(info.success());
    }

    @NonNull
    public static FileResult<Boolean> isDirectory(@NonNull Context context,
                                                  @NonNull FileTarget target) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isPath()) {
            FileResult<File> resolved = resolvePath(context, target.asPath());
            if (!resolved.success()) {
                return FileResult.failure(resolved.requireFailure());
            }
            return PathFileStore.isDirectory(resolved.value());
        }
        UriTarget uri = target.asUri();
        if (uri.uriKind() == UriKind.TREE) {
            return FileResult.ok(
                    SafTreeOps.openTree(context, uri.uri(), uri.relativePath()) != null);
        }
        return FileResult.ok(false);
    }

    @NonNull
    public static FileResult<Long> length(@NonNull Context context,
                                          @NonNull FileTarget target) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isPath()) {
            FileResult<File> resolved = resolvePath(context, target.asPath());
            if (!resolved.success()) {
                return FileResult.failure(resolved.requireFailure());
            }
            return PathFileStore.length(resolved.value());
        }
        FileResult<UriFileInfo> info =
                UriFileStore.inspect(context, target.asUri());
        if (!info.success()) {
            return FileResult.failure(info.requireFailure());
        }
        return FileResult.ok(info.value().sizeHint());
    }

    @NonNull
    public static FileResult<Long> lastModified(@NonNull Context context,
                                                @NonNull FileTarget target) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isPath()) {
            FileResult<File> resolved = resolvePath(context, target.asPath());
            if (!resolved.success()) {
                return FileResult.failure(resolved.requireFailure());
            }
            return PathFileStore.lastModified(resolved.value());
        }
        return FileResult.failure(
                FileFailureCode.UNSUPPORTED, "lastModified by URI is unsupported");
    }

    @NonNull
    public static FileResult<Void> setLastModified(@NonNull Context context,
                                                   @NonNull PathTarget target,
                                                   long millis) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        FileResult<File> resolved = resolvePath(context, target);
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        return PathFileStore.setLastModified(resolved.value(), millis);
    }

    @NonNull
    public static FileResult<UriFileInfo> inspect(@NonNull Context context,
                                                  @NonNull UriTarget target) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        return UriFileStore.inspect(context, target);
    }

    @NonNull
    public static FileResult<List<FileEntry>> list(@NonNull Context context,
                                                   @NonNull FileTarget target) {
        return list(context, target, null);
    }

    @NonNull
    public static FileResult<List<FileEntry>> list(
            @NonNull Context context,
            @NonNull FileTarget target,
            @Nullable Predicate<FileEntry> filter) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isPath()) {
            FileResult<File> resolved = resolvePath(context, target.asPath());
            if (!resolved.success()) {
                return FileResult.failure(resolved.requireFailure());
            }
            return PathFileStore.list(resolved.value(), filter);
        }
        UriTarget uri = target.asUri();
        if (uri.uriKind() == UriKind.TREE) {
            return SafTreeOps.listTree(context, uri.uri(), uri.relativePath(), filter);
        }
        return FileResult.failure(
                FileFailureCode.UNSUPPORTED, "list requires path or tree uri");
    }

    @NonNull
    public static FileResult<List<FileEntry>> listRecursive(
            @NonNull Context context,
            @NonNull FileTarget target,
            @Nullable Predicate<FileEntry> filter) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isUri()) {
            UriTarget uri = target.asUri();
            if (uri.uriKind() == UriKind.TREE) {
                return SafTreeOps.listTreeRecursive(
                        context, uri.uri(), uri.relativePath(), filter);
            }
            return FileResult.failure(
                    FileFailureCode.UNSUPPORTED,
                    "listRecursive requires path or tree uri");
        }
        FileResult<File> resolved = resolvePath(context, target.asPath());
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        return PathFileStore.listRecursive(resolved.value(), filter);
    }

    @NonNull
    public static FileResult<File> ensureDir(@NonNull Context context,
                                             @NonNull PathTarget target) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        FileResult<File> resolved = resolvePath(context, target);
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        return PathFileStore.ensureDir(resolved.value());
    }

    @NonNull
    public static FileResult<File> mkdir(@NonNull Context context,
                                         @NonNull PathTarget target) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        FileResult<File> resolved = resolvePath(context, target);
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        File dir = resolved.value();
        try {
            if (dir.exists() || dir.mkdir()) {
                return FileResult.ok(dir);
            }
            return FileResult.failure(FileFailureCode.IO_ERROR, "cannot mkdir: " + dir);
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot mkdir: " + dir, e);
        }
    }

    @NonNull
    public static FileResult<File> mkdirs(@NonNull Context context,
                                          @NonNull PathTarget target) {
        return ensureDir(context, target);
    }

    @NonNull
    public static FileResult<Void> delete(@NonNull Context context,
                                          @NonNull FileTarget target,
                                          boolean recursive) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isPath()) {
            FileResult<File> resolved = resolvePath(context, target.asPath());
            if (!resolved.success()) {
                return FileResult.failure(resolved.requireFailure());
            }
            return PathFileStore.delete(resolved.value(), recursive);
        }
        UriTarget uri = target.asUri();
        if (uri.uriKind() == UriKind.TREE) {
            if (uri.relativePath() == null) {
                return FileResult.failure(
                        FileFailureCode.UNSUPPORTED,
                        "deleting a whole tree is unsupported; delete tree-relative paths");
            }
            return SafTreeOps.deletePath(context, uri.uri(), uri.relativePath());
        }
        return UriFileStore.delete(context, uri);
    }

    @NonNull
    public static FileResult<File> createNewFile(@NonNull Context context,
                                                 @NonNull PathTarget target,
                                                 @NonNull ConflictPolicy conflict) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        FileResult<File> resolved = resolvePath(context, target);
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        return PathFileStore.createNewFile(resolved.value(), conflict);
    }

    @NonNull
    public static FileResult<byte[]> readBytes(@NonNull Context context,
                                               @NonNull FileTarget target,
                                               long maxBytes) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.READ);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isPath()) {
            FileResult<File> resolved = resolvePath(context, target.asPath());
            if (!resolved.success()) {
                return FileResult.failure(resolved.requireFailure());
            }
            return PathFileStore.readBytes(resolved.value(), maxBytes);
        }
        return UriFileStore.readBytes(context, target.asUri(), maxBytes);
    }

    @NonNull
    public static FileResult<String> readText(@NonNull Context context,
                                              @NonNull FileTarget target,
                                              @NonNull Charset charset,
                                              long maxBytes) {
        FileResult<byte[]> bytes = readBytes(context, target, maxBytes);
        if (!bytes.success()) {
            return FileResult.failure(bytes.requireFailure());
        }
        return FileResult.ok(new String(bytes.value(), charset));
    }

    @NonNull
    public static FileResult<Void> writeBytes(@NonNull Context context,
                                              @NonNull FileTarget target,
                                              @NonNull byte[] bytes,
                                              @NonNull ConflictPolicy conflict) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isUri()) {
            return UriFileStore.writeBytes(context, target.asUri(), bytes);
        }
        FileResult<File> resolved = resolvePath(context, target.asPath());
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        FileResult<Void> space = ensureMinUsableSpace(resolved.value());
        if (space != null) {
            return space;
        }
        return PathFileStore.writeBytes(resolved.value(), bytes, conflict);
    }

    @NonNull
    public static FileResult<Void> writeText(@NonNull Context context,
                                             @NonNull FileTarget target,
                                             @NonNull String text,
                                             @NonNull Charset charset,
                                             @NonNull ConflictPolicy conflict) {
        return writeBytes(context, target, text.getBytes(charset), conflict);
    }

    @NonNull
    public static FileResult<Void> appendBytes(@NonNull Context context,
                                               @NonNull FileTarget target,
                                               @NonNull byte[] bytes) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        if (target.isUri()) {
            FileResult<OutputStream> opened = UriFileStore.openOutputStream(
                    context,
                    target.asUri(),
                    EnumSet.of(OpenOptions.WRITE, OpenOptions.APPEND));
            if (!opened.success()) {
                return FileResult.failure(opened.requireFailure());
            }
            try (OutputStream out = opened.value()) {
                out.write(bytes);
                out.flush();
                return FileResult.ok(null);
            } catch (java.io.IOException e) {
                return FileResult.failure(
                        FileFailureCode.IO_ERROR, "cannot append", e);
            }
        }
        FileResult<File> resolved = resolvePath(context, target.asPath());
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        FileResult<Void> space = ensureMinUsableSpace(resolved.value());
        if (space != null) {
            return space;
        }
        return PathFileStore.appendBytes(resolved.value(), bytes);
    }

    @NonNull
    public static FileResult<Void> appendText(@NonNull Context context,
                                              @NonNull FileTarget target,
                                              @NonNull String text,
                                              @NonNull Charset charset) {
        return appendBytes(context, target, text.getBytes(charset));
    }

    @NonNull
    public static FileResult<Void> writeAtomic(@NonNull Context context,
                                               @NonNull PathTarget target,
                                               @NonNull byte[] bytes) {
        return writeAtomic(context, target, out -> out.write(bytes));
    }

    @NonNull
    public static FileResult<Void> writeAtomic(@NonNull Context context,
                                               @NonNull PathTarget target,
                                               @NonNull StreamWriter writer) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        FileResult<File> resolved = resolvePath(context, target);
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        FileResult<Void> space = ensureMinUsableSpace(resolved.value());
        if (space != null) {
            return space;
        }
        return AtomicPathWriter.writeAtomic(resolved.value(), writer::write);
    }

    @NonNull
    public static FileResult<Void> truncate(@NonNull Context context,
                                            @NonNull PathTarget target,
                                            long size) {
        AccessCheckResult gateResult = gate(context, target, AccessMode.WRITE);
        if (gateResult != null) {
            return denied(gateResult);
        }
        FileResult<File> resolved = resolvePath(context, target);
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        FileResult<Void> space = ensureMinUsableSpace(resolved.value());
        if (space != null) {
            return space;
        }
        return PathFileStore.truncate(resolved.value(), size);
    }

    @NonNull
    public static FileResult<Long> copy(@NonNull Context context,
                                        @NonNull FileTarget source,
                                        @NonNull FileTarget destination,
                                        @NonNull ConflictPolicy conflict,
                                        long maxBytes) {
        AccessCheckResult sourceGate = gate(context, source, AccessMode.READ);
        if (sourceGate != null) {
            return denied(sourceGate);
        }
        AccessCheckResult destGate = gate(context, destination, AccessMode.WRITE);
        if (destGate != null) {
            return denied(destGate);
        }
        if (source.isPath() && destination.isPath()) {
            FileResult<File> src = resolvePath(context, source.asPath());
            FileResult<File> dst = resolvePath(context, destination.asPath());
            if (!src.success()) {
                return FileResult.failure(src.requireFailure());
            }
            if (!dst.success()) {
                return FileResult.failure(dst.requireFailure());
            }
            FileResult<Void> space = ensureMinUsableSpace(dst.value());
            if (space != null) {
                return FileResult.failure(space.requireFailure());
            }
            return PathFileStore.copy(src.value(), dst.value(), conflict, maxBytes);
        }
        if (source.isUri() && destination.isPath()) {
            FileResult<File> dst = resolvePath(context, destination.asPath());
            if (!dst.success()) {
                return FileResult.failure(dst.requireFailure());
            }
            FileResult<Void> space = ensureMinUsableSpace(dst.value());
            if (space != null) {
                return FileResult.failure(space.requireFailure());
            }
            return UriFileStore.copyTo(
                    context, source.asUri(), dst.value(), maxBytes, conflict);
        }
        if (source.isPath() && destination.isUri()) {
            UriTarget tree = destination.asUri();
            if (tree.uriKind() != UriKind.TREE || tree.relativePath() == null) {
                return FileResult.failure(
                        FileFailureCode.INVALID_TARGET,
                        "path-to-uri copy requires tree target with relativePath");
            }
            FileResult<File> src = resolvePath(context, source.asPath());
            if (!src.success()) {
                return FileResult.failure(src.requireFailure());
            }
            FileResult<SafFile> copied = SafTreeOps.copyFileToTree(
                    context,
                    tree.uri(),
                    tree.relativePath(),
                    src.value(),
                    src.value().getName(),
                    "application/octet-stream",
                    maxBytes,
                    conflict);
            if (!copied.success()) {
                return FileResult.failure(copied.requireFailure());
            }
            return FileResult.ok(src.value().length());
        }
        return FileResult.failure(
                FileFailureCode.UNSUPPORTED, "uri-to-uri copy is unsupported");
    }

    @NonNull
    public static FileResult<Void> move(@NonNull Context context,
                                        @NonNull FileTarget source,
                                        @NonNull FileTarget destination,
                                        @NonNull ConflictPolicy conflict) {
        AccessCheckResult sourceGate = gate(context, source, AccessMode.READ);
        if (sourceGate != null) {
            return denied(sourceGate);
        }
        AccessCheckResult destGate = gate(context, destination, AccessMode.WRITE);
        if (destGate != null) {
            return denied(destGate);
        }
        if (source.isPath() && destination.isPath()) {
            FileResult<File> src = resolvePath(context, source.asPath());
            FileResult<File> dst = resolvePath(context, destination.asPath());
            if (!src.success()) {
                return FileResult.failure(src.requireFailure());
            }
            if (!dst.success()) {
                return FileResult.failure(dst.requireFailure());
            }
            return PathFileStore.move(src.value(), dst.value(), conflict);
        }
        if (source.isUri() && destination.isPath()) {
            FileResult<File> dst = resolvePath(context, destination.asPath());
            if (!dst.success()) {
                return FileResult.failure(dst.requireFailure());
            }
            FileResult<Long> copied = UriFileStore.copyTo(
                    context, source.asUri(), dst.value(), NO_SIZE_LIMIT, conflict);
            if (!copied.success()) {
                return FileResult.failure(copied.requireFailure());
            }
            return UriFileStore.delete(context, source.asUri());
        }
        if (source.isPath() && destination.isUri()) {
            FileResult<Long> copied = copy(
                    context, source, destination, conflict, NO_SIZE_LIMIT);
            if (!copied.success()) {
                return FileResult.failure(copied.requireFailure());
            }
            FileResult<File> src = resolvePath(context, source.asPath());
            if (!src.success()) {
                return FileResult.failure(src.requireFailure());
            }
            return PathFileStore.delete(src.value(), false);
        }
        return FileResult.failure(
                FileFailureCode.UNSUPPORTED, "uri-to-uri move is unsupported");
    }

    @NonNull
    public static FileResult<File> copyUriInto(@NonNull Context context,
                                               @NonNull UriTarget source,
                                               @NonNull PathTarget destinationDir,
                                               @Nullable String preferredName,
                                               long maxBytes,
                                               @NonNull ConflictPolicy conflict) {
        AccessCheckResult sourceGate = gate(context, source, AccessMode.READ);
        if (sourceGate != null) {
            return denied(sourceGate);
        }
        AccessCheckResult destGate = gate(
                context, destinationDir, AccessMode.WRITE);
        if (destGate != null) {
            return denied(destGate);
        }
        FileResult<File> dir = resolvePath(context, destinationDir);
        if (!dir.success()) {
            return FileResult.failure(dir.requireFailure());
        }
        return UriFileStore.copyInto(
                context, source, dir.value(), preferredName, maxBytes, conflict);
    }

    @NonNull
    public static SafTreeStore treeStore(@NonNull Context context) {
        return new PrefsSafTreeStore(context.getApplicationContext());
    }

    @NonNull
    public static SafTreePicker treePicker(@NonNull Context context) {
        return new AndroidSafTreePicker(treeStore(context));
    }

    @NonNull
    public static Intent createTreePickerIntent() {
        return SafTreeOps.createTreePickerIntent();
    }

    @NonNull
    public static FileResult<Void> takePersistableTree(@NonNull Context context,
                                                       @NonNull Uri treeUri,
                                                       int modeFlags) {
        return SafTreeOps.takePersistable(context, treeUri, modeFlags);
    }

    @NonNull
    public static FileResult<Void> releasePersistableTree(@NonNull Context context,
                                                          @NonNull Uri treeUri,
                                                          int modeFlags) {
        return SafTreeOps.releasePersistable(context, treeUri, modeFlags);
    }

    @NonNull
    public static List<Uri> listPersistedTreeUris(@NonNull Context context) {
        return SafTreeOps.listPersisted(context);
    }

    public static boolean isTreeAccessible(@NonNull Context context,
                                           @Nullable Uri treeUri) {
        return SafTreeOps.isTreeAccessible(context, treeUri);
    }

    @Nullable
    public static SafDirectory openTree(@NonNull Context context,
                                        @NonNull Uri treeUri) {
        return SafTreeOps.openTree(context, treeUri);
    }

    @NonNull
    public static FileResult<SafFile> copyFileToTree(@NonNull Context context,
                                                     @NonNull Uri treeUri,
                                                     @NonNull File source,
                                                     @NonNull String name,
                                                     @NonNull String mimeType,
                                                     @NonNull ConflictPolicy conflict) {
        AccessCheckResult treeGate = gate(
                context, UriTarget.tree(treeUri), AccessMode.WRITE);
        if (treeGate != null) {
            return denied(treeGate);
        }
        File parent = source.getParentFile() != null
                ? source.getParentFile()
                : source.getAbsoluteFile().getParentFile() != null
                ? source.getAbsoluteFile().getParentFile()
                : new File(".");
        AccessCheckResult sourceGate = gate(
                context, PathTarget.custom(parent, source.getName()), AccessMode.READ);
        if (sourceGate != null) {
            return denied(sourceGate);
        }
        return SafTreeOps.copyFileToTree(
                context,
                treeUri,
                null,
                source,
                name,
                mimeType,
                NO_SIZE_LIMIT,
                conflict);
    }

    @NonNull
    public static FileResult<SafFile> copyFileToTree(@NonNull Context context,
                                                     @NonNull Uri treeUri,
                                                     @NonNull PathTarget source,
                                                     @NonNull String name,
                                                     @NonNull String mimeType,
                                                     @NonNull ConflictPolicy conflict) {
        FileResult<File> resolved = resolvePath(context, source);
        if (!resolved.success()) {
            return FileResult.failure(resolved.requireFailure());
        }
        return copyFileToTree(
                context, treeUri, resolved.value(), name, mimeType, conflict);
    }

    @NonNull
    private static AccessEnvironment environment(@NonNull Context context) {
        return new AndroidAccessEnvironment(context);
    }

    /**
     * Path 写前空间检查；不足返回失败结果，充足返回 null。
     */
    @Nullable
    static FileResult<Void> ensureMinUsableSpace(@NonNull File path) {
        return ensureMinUsableSpace(path, DEFAULT_MIN_USABLE_SPACE_BYTES);
    }

    @Nullable
    static FileResult<Void> ensureMinUsableSpace(@NonNull File path, long minBytes) {
        if (minBytes <= 0L) {
            return null;
        }
        File probe = path;
        if (!probe.exists()) {
            File parent = probe.getParentFile();
            if (parent != null) {
                probe = parent;
            }
        }
        FileResult<Long> space = PathFileStore.usableSpace(probe);
        if (!space.success()) {
            return FileResult.failure(space.requireFailure());
        }
        if (space.value() < minBytes) {
            return FileResult.failure(
                    FileFailureCode.LOW_DISK,
                    "usable space " + space.value() + " < " + minBytes);
        }
        return null;
    }

    @Nullable
    private static AccessCheckResult gate(@NonNull Context context,
                                          @NonNull FileTarget target,
                                          @NonNull AccessMode mode) {
        AccessCheckResult result = AccessGate.check(target, mode, environment(context));
        if (!result.isAllowed()) {
            YoLog.w(TAG, "访问被拒绝：目标=" + target
                    + "，模式=" + mode + "，原因=" + result.reason());
            return result;
        }
        return null;
    }

    @NonNull
    private static <T> FileResult<T> denied(@NonNull AccessCheckResult result) {
        return FileResult.failure(
                FileFailureCode.DENIED,
                "access denied: " + result.reason(),
                null,
                result);
    }
}
