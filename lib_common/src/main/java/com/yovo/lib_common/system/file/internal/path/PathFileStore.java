package com.yovo.lib_common.system.file.internal.path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.internal.util.IoUtil;
import com.yovo.lib_common.internal.util.FileNames;
import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.FileEntry;
import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.OpenOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 直接文件系统 CRUD：所有方法返回 FileResult，不向调用方抛 IO 异常。
 */
public final class PathFileStore {

    public static final long NO_SIZE_LIMIT = -1L;

    private static final int BUFFER_SIZE = 64 * 1024;

    private PathFileStore() {
    }

    @NonNull
    public static FileResult<File> ensureDir(@NonNull File dir) {
        if (dir.isDirectory()) {
            return FileResult.ok(dir);
        }
        if (dir.exists()) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "exists but not a directory: " + dir);
        }
        try {
            if (dir.mkdirs()) {
                return FileResult.ok(dir);
            }
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot create directory: " + dir, e);
        }
        return FileResult.failure(FileFailureCode.IO_ERROR, "cannot create directory: " + dir);
    }

    @NonNull
    public static FileResult<Boolean> exists(@NonNull File file) {
        try {
            return FileResult.ok(file.exists());
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot check exists: " + file, e);
        }
    }

    @NonNull
    public static FileResult<Boolean> isDirectory(@NonNull File file) {
        try {
            return FileResult.ok(file.isDirectory());
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot check directory: " + file, e);
        }
    }

    @NonNull
    public static FileResult<Long> length(@NonNull File file) {
        try {
            return FileResult.ok(file.length());
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot read length: " + file, e);
        }
    }

    @NonNull
    public static FileResult<Long> lastModified(@NonNull File file) {
        try {
            return FileResult.ok(file.lastModified());
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot read lastModified: " + file, e);
        }
    }

    @NonNull
    public static FileResult<Void> setLastModified(@NonNull File file, long millis) {
        try {
            if (!file.exists()) {
                return FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + file);
            }
            if (!file.setLastModified(millis)) {
                return FileResult.failure(
                        FileFailureCode.IO_ERROR, "cannot set lastModified: " + file);
            }
            return FileResult.ok(null);
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot set lastModified: " + file, e);
        }
    }

    @NonNull
    public static FileResult<List<FileEntry>> list(@NonNull File dir,
                                                   @Nullable Predicate<FileEntry> filter) {
        File[] children;
        try {
            children = dir.listFiles();
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot list directory: " + dir, e);
        }
        if (children == null) {
            return dir.exists()
                    ? FileResult.failure(FileFailureCode.IO_ERROR, "cannot list: " + dir)
                    : FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + dir);
        }
        List<FileEntry> result = new ArrayList<>();
        for (File child : children) {
            FileEntry entry = FileEntry.of(child);
            if (filter == null || filter.test(entry)) {
                result.add(entry);
            }
        }
        return FileResult.ok(result);
    }

    @NonNull
    public static FileResult<List<FileEntry>> listRecursive(
            @NonNull File dir,
            @Nullable Predicate<FileEntry> filter) {
        List<FileEntry> result = new ArrayList<>();
        FileResult<Void> walk = walk(dir, result, filter, 0);
        if (!walk.success()) {
            return FileResult.failure(walk.requireFailure());
        }
        return FileResult.ok(result);
    }

    @NonNull
    private static FileResult<Void> walk(@NonNull File dir,
                                         @NonNull List<FileEntry> sink,
                                         @Nullable Predicate<FileEntry> filter,
                                         int depth) {
        if (depth > 64) {
            return FileResult.failure(FileFailureCode.IO_ERROR, "max recursion depth exceeded");
        }
        File[] children;
        try {
            children = dir.listFiles();
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot list directory: " + dir, e);
        }
        if (children == null) {
            return dir.exists()
                    ? FileResult.failure(FileFailureCode.IO_ERROR, "cannot list: " + dir)
                    : FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + dir);
        }
        for (File child : children) {
            FileEntry entry = FileEntry.of(child);
            if (filter == null || filter.test(entry)) {
                sink.add(entry);
            }
            if (child.isDirectory()) {
                FileResult<Void> sub = walk(child, sink, filter, depth + 1);
                if (!sub.success()) {
                    return sub;
                }
            }
        }
        return FileResult.ok(null);
    }

    @NonNull
    public static FileResult<Void> delete(@NonNull File file, boolean recursive) {
        try {
            if (!file.exists()) {
                return FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + file);
            }
            if (file.isDirectory() && recursive) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        FileResult<Void> deleted = delete(child, true);
                        if (!deleted.success()) {
                            return deleted;
                        }
                    }
                }
            }
            if (file.delete()) {
                return FileResult.ok(null);
            }
            return FileResult.failure(FileFailureCode.IO_ERROR, "cannot delete: " + file);
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot delete: " + file, e);
        }
    }

    @NonNull
    public static FileResult<File> createNewFile(@NonNull File file,
                                                 @NonNull ConflictPolicy conflict) {
        try {
            if (file.exists()) {
                if (conflict == ConflictPolicy.FAIL) {
                    return FileResult.failure(
                            FileFailureCode.ALREADY_EXISTS, "already exists: " + file);
                }
                if (conflict == ConflictPolicy.RENAME_UNIQUE) {
                    File parent = file.getParentFile();
                    String unique = uniqueName(parent, file.getName());
                    File renamed = new File(parent, unique);
                    if (renamed.createNewFile()) {
                        return FileResult.ok(renamed);
                    }
                    return FileResult.failure(
                            FileFailureCode.IO_ERROR, "cannot create: " + renamed);
                }
                return FileResult.ok(file);
            }
            if (file.createNewFile()) {
                return FileResult.ok(file);
            }
            return FileResult.failure(FileFailureCode.IO_ERROR, "cannot create: " + file);
        } catch (IOException | SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot create: " + file, e);
        }
    }

    @NonNull
    public static FileResult<byte[]> readBytes(@NonNull File file, long maxBytes) {
        if (!file.isFile()) {
            return FileResult.failure(
                    file.exists() ? FileFailureCode.IO_ERROR : FileFailureCode.NOT_FOUND,
                    file.exists() ? "not a file: " + file : "not found: " + file);
        }
        if (maxBytes == 0L || maxBytes < NO_SIZE_LIMIT) {
            return FileResult.failure(
                    FileFailureCode.INVALID_TARGET, "maxBytes must be positive or NO_SIZE_LIMIT");
        }
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0L;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (maxBytes != NO_SIZE_LIMIT && total > maxBytes) {
                    return FileResult.failure(
                            FileFailureCode.SIZE_LIMIT_EXCEEDED,
                            "file exceeds limit: " + maxBytes + " bytes");
                }
                out.write(buffer, 0, read);
            }
            return FileResult.ok(out.toByteArray());
        } catch (IOException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot read: " + file, e);
        }
    }

    @NonNull
    public static FileResult<String> readText(@NonNull File file,
                                              @NonNull Charset charset,
                                              long maxBytes) {
        FileResult<byte[]> bytes = readBytes(file, maxBytes);
        if (!bytes.success()) {
            return FileResult.failure(bytes.requireFailure());
        }
        return FileResult.ok(new String(bytes.value(), charset));
    }

    @NonNull
    public static FileResult<Void> writeBytes(@NonNull File file,
                                              @NonNull byte[] bytes,
                                              @NonNull ConflictPolicy conflict) {
        if (conflict == ConflictPolicy.FAIL && file.exists()) {
            return FileResult.failure(
                    FileFailureCode.ALREADY_EXISTS, "already exists: " + file);
        }
        FileResult<File> target = resolveWriteTarget(file, conflict);
        if (!target.success()) {
            return FileResult.failure(target.requireFailure());
        }
        FileResult<OutputStream> stream = openOutputStream(
                target.value(),
                EnumSet.of(OpenOptions.WRITE, OpenOptions.CREATE, OpenOptions.TRUNCATE));
        if (!stream.success()) {
            return FileResult.failure(stream.requireFailure());
        }
        try (OutputStream out = stream.value()) {
            out.write(bytes);
            out.flush();
            return FileResult.ok(null);
        } catch (IOException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot write: " + target.value(), e);
        }
    }

    @NonNull
    public static FileResult<Void> writeText(@NonNull File file,
                                             @NonNull String text,
                                             @NonNull Charset charset,
                                             @NonNull ConflictPolicy conflict) {
        return writeBytes(file, text.getBytes(charset), conflict);
    }

    @NonNull
    public static FileResult<Void> appendBytes(@NonNull File file,
                                               @NonNull byte[] bytes) {
        FileResult<OutputStream> stream = openOutputStream(
                file,
                EnumSet.of(OpenOptions.WRITE, OpenOptions.CREATE, OpenOptions.APPEND));
        if (!stream.success()) {
            return FileResult.failure(stream.requireFailure());
        }
        try (OutputStream out = stream.value()) {
            out.write(bytes);
            out.flush();
            return FileResult.ok(null);
        } catch (IOException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot append: " + file, e);
        }
    }

    @NonNull
    public static FileResult<Void> appendText(@NonNull File file,
                                              @NonNull String text,
                                              @NonNull Charset charset) {
        return appendBytes(file, text.getBytes(charset));
    }

    @NonNull
    public static FileResult<InputStream> openInputStream(@NonNull File file) {
        if (!file.isFile()) {
            return FileResult.failure(
                    file.exists() ? FileFailureCode.IO_ERROR : FileFailureCode.NOT_FOUND,
                    file.exists() ? "not a file: " + file : "not found: " + file);
        }
        try {
            return FileResult.ok(new FileInputStream(file));
        } catch (IOException | SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot open: " + file, e);
        }
    }

    @NonNull
    public static FileResult<OutputStream> openOutputStream(
            @NonNull File file,
            @NonNull Set<OpenOptions> options) {
        if (options.contains(OpenOptions.READ)) {
            return FileResult.failure(
                    FileFailureCode.INVALID_TARGET, "READ is not valid for output stream");
        }
        if (!options.contains(OpenOptions.WRITE)) {
            return FileResult.failure(
                    FileFailureCode.INVALID_TARGET, "WRITE option is required");
        }
        if (options.contains(OpenOptions.CREATE_NEW)
                && options.contains(OpenOptions.APPEND)) {
            return FileResult.failure(
                    FileFailureCode.INVALID_TARGET, "CREATE_NEW cannot combine with APPEND");
        }
        boolean append = options.contains(OpenOptions.APPEND);
        boolean truncate = options.contains(OpenOptions.TRUNCATE);
        if (append && truncate) {
            return FileResult.failure(
                    FileFailureCode.INVALID_TARGET, "APPEND cannot combine with TRUNCATE");
        }
        if (!append && !truncate) {
            return FileResult.failure(
                    FileFailureCode.INVALID_TARGET,
                    "WRITE requires APPEND or TRUNCATE (no implicit truncation)");
        }
        if (file.exists()) {
            if (options.contains(OpenOptions.CREATE_NEW)) {
                return FileResult.failure(
                        FileFailureCode.ALREADY_EXISTS, "already exists: " + file);
            }
        } else if (!options.contains(OpenOptions.CREATE)
                && !options.contains(OpenOptions.CREATE_NEW)) {
            return FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + file);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            FileResult<File> ensured = ensureDir(parent);
            if (!ensured.success()) {
                return FileResult.failure(ensured.requireFailure());
            }
        }
        try {
            return FileResult.ok(new FileOutputStream(file, append));
        } catch (IOException | SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot open: " + file, e);
        }
    }

    @NonNull
    public static FileResult<Void> truncate(@NonNull File file, long size) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(size);
            return FileResult.ok(null);
        } catch (IOException | SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot truncate: " + file, e);
        }
    }

    @NonNull
    public static FileResult<Long> usableSpace(@NonNull File file) {
        try {
            return FileResult.ok(file.getUsableSpace());
        } catch (SecurityException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot query usable space: " + file, e);
        }
    }

    @NonNull
    public static FileResult<Long> copy(@NonNull File source,
                                        @NonNull File destination,
                                        @NonNull ConflictPolicy conflict,
                                        long maxBytes) {
        if (!source.isFile()) {
            return FileResult.failure(
                    source.exists() ? FileFailureCode.IO_ERROR : FileFailureCode.NOT_FOUND,
                    source.exists() ? "not a file: " + source : "not found: " + source);
        }
        File target = resolveDestination(destination, conflict);
        if (target == null) {
            return FileResult.failure(
                    FileFailureCode.ALREADY_EXISTS, "destination exists: " + destination);
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            FileResult<File> ensured = ensureDir(parent);
            if (!ensured.success()) {
                return FileResult.failure(ensured.requireFailure());
            }
        }
        File temp = new File(parent != null ? parent : new File("."),
                target.getName() + ".tmp-" + System.currentTimeMillis());
        long total = 0L;
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (maxBytes != NO_SIZE_LIMIT && total > maxBytes) {
                    IoUtil.deleteQuietly(temp);
                    return FileResult.failure(
                            FileFailureCode.SIZE_LIMIT_EXCEEDED,
                            "copy exceeds limit: " + maxBytes + " bytes");
                }
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (IOException e) {
            IoUtil.deleteQuietly(temp);
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot copy: " + source, e);
        }
        if (!IoUtil.moveReplace(temp, target)) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot replace destination: " + target);
        }
        return FileResult.ok(total);
    }

    @NonNull
    public static FileResult<Void> move(@NonNull File source,
                                        @NonNull File destination,
                                        @NonNull ConflictPolicy conflict) {
        if (!source.exists()) {
            return FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + source);
        }
        File target = resolveDestination(destination, conflict);
        if (target == null) {
            return FileResult.failure(
                    FileFailureCode.ALREADY_EXISTS, "destination exists: " + destination);
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            FileResult<File> ensured = ensureDir(parent);
            if (!ensured.success()) {
                return FileResult.failure(ensured.requireFailure());
            }
        }
        if (source.renameTo(target)) {
            return FileResult.ok(null);
        }
        FileResult<Long> copied = copy(source, target, conflict, NO_SIZE_LIMIT);
        if (!copied.success()) {
            return FileResult.failure(copied.requireFailure());
        }
        IoUtil.deleteQuietly(source);
        return FileResult.ok(null);
    }

    @NonNull
    public static String uniqueName(@Nullable File dir, @NonNull String baseName) {
        String name = baseName;
        String extension = "";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0 && dot < baseName.length() - 1) {
            name = baseName.substring(0, dot);
            extension = baseName.substring(dot);
        }
        File candidate = new File(dir, baseName);
        int index = 1;
        while (candidate.exists()) {
            candidate = new File(dir, name + "(" + index + ")" + extension);
            index++;
        }
        return candidate.getName();
    }

    @NonNull
    public static String safeBaseName(@Nullable String original) {
        return FileNames.sanitize(original, "file");
    }

    @NonNull
    private static FileResult<File> resolveWriteTarget(@NonNull File file,
                                                       @NonNull ConflictPolicy conflict) {
        if (!file.exists() || conflict != ConflictPolicy.RENAME_UNIQUE) {
            return FileResult.ok(file);
        }
        File parent = file.getParentFile();
        String unique = uniqueName(parent, file.getName());
        return FileResult.ok(new File(parent, unique));
    }

    @Nullable
    private static File resolveDestination(@NonNull File destination,
                                           @NonNull ConflictPolicy conflict) {
        if (!destination.exists() || conflict == ConflictPolicy.OVERWRITE) {
            return destination;
        }
        if (conflict == ConflictPolicy.FAIL) {
            return null;
        }
        File parent = destination.getParentFile();
        String unique = uniqueName(parent, destination.getName());
        return new File(parent, unique);
    }
}
