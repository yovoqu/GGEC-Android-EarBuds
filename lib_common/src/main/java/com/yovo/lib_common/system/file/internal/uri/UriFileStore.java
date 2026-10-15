package com.yovo.lib_common.system.file.internal.uri;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.internal.util.FileNames;
import com.yovo.lib_common.internal.util.IoUtil;
import com.yovo.lib_common.system.file.internal.path.PathFileStore;
import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.UriFileInfo;
import com.yovo.lib_common.system.file.model.UriTarget;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;

import com.yovo.lib_common.system.file.internal.saf.SafTreeOps;
import com.yovo.lib_common.system.file.model.OpenOptions;

/**
 * 通用 content URI 元数据、读写与导入（吸收原 UriFileImporter）。
 */
public final class UriFileStore {

    public static final long NO_SIZE_LIMIT = -1L;

    private static final int BUFFER_SIZE = 16 * 1024;

    private UriFileStore() {
    }

    @NonNull
    public static FileResult<UriFileInfo> inspect(@NonNull Context context,
                                                  @NonNull UriTarget target) {
        Uri uri = target.uri();
        ContentResolver resolver = context.getContentResolver();
        String displayName = null;
        long size = -1L;
        Cursor cursor = null;
        try {
            cursor = resolver.query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                    null,
                    null,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex);
                }
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = uri.getLastPathSegment();
        }
        int slash = Math.max(
                displayName.lastIndexOf('/'), displayName.lastIndexOf('\\'));
        if (slash >= 0) {
            displayName = displayName.substring(slash + 1);
        }
        displayName = FileNames.sanitize(displayName, "imported");
        return FileResult.ok(new UriFileInfo(
                displayName,
                resolver.getType(uri),
                size,
                documentSupportsWrite(context, uri)));
    }

    @NonNull
    public static FileResult<InputStream> openInputStream(@NonNull Context context,
                                                          @NonNull UriTarget target) {
        try {
            InputStream input = context.getContentResolver().openInputStream(target.uri());
            if (input == null) {
                return FileResult.failure(
                        FileFailureCode.IO_ERROR, "provider returned null stream: " + target.uri());
            }
            return FileResult.ok(input);
        } catch (IOException | RuntimeException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot open: " + target.uri(), e);
        }
    }

    @NonNull
    public static FileResult<byte[]> readBytes(@NonNull Context context,
                                               @NonNull UriTarget target,
                                               long maxBytes) {
        if (maxBytes == 0L || maxBytes < NO_SIZE_LIMIT) {
            return FileResult.failure(
                    FileFailureCode.INVALID_TARGET,
                    "maxBytes must be positive or NO_SIZE_LIMIT");
        }
        FileResult<InputStream> opened = openInputStream(context, target);
        if (!opened.success()) {
            return FileResult.failure(opened.requireFailure());
        }
        try (InputStream input = opened.value()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (maxBytes != NO_SIZE_LIMIT && total > maxBytes) {
                    return FileResult.failure(
                            FileFailureCode.SIZE_LIMIT_EXCEEDED,
                            "URI content exceeds limit: " + maxBytes);
                }
                out.write(buffer, 0, read);
            }
            return FileResult.ok(out.toByteArray());
        } catch (IOException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot read: " + target.uri(), e);
        }
    }

    @NonNull
    public static FileResult<String> readText(@NonNull Context context,
                                              @NonNull UriTarget target,
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
                                              @NonNull UriTarget target,
                                              @NonNull byte[] bytes) {
        if (target.uriKind() != com.yovo.lib_common.system.file.model.UriKind.DOCUMENT) {
            return FileResult.failure(
                    FileFailureCode.UNSUPPORTED, "write by URI only supports DOCUMENT");
        }
        try (OutputStream out = context.getContentResolver()
                .openOutputStream(target.uri(), "w")) {
            if (out == null) {
                return FileResult.failure(
                        FileFailureCode.IO_ERROR, "provider returned null stream");
            }
            out.write(bytes);
            out.flush();
            return FileResult.ok(null);
        } catch (IOException | RuntimeException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot write: " + target.uri(), e);
        }
    }

    @NonNull
    public static FileResult<OutputStream> openOutputStream(
            @NonNull Context context,
            @NonNull UriTarget target,
            @NonNull Set<OpenOptions> options) {
        if (options.contains(OpenOptions.READ)) {
            return FileResult.failure(
                    FileFailureCode.INVALID_TARGET, "READ is not valid for output stream");
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
        if (target.uriKind() == com.yovo.lib_common.system.file.model.UriKind.TREE) {
            if (target.relativePath() == null) {
                return FileResult.failure(
                        FileFailureCode.INVALID_TARGET, "tree output requires relativePath");
            }
            return SafTreeOps.openOutputStream(
                    context, target.uri(), target.relativePath(), options);
        }
        try {
            String mode = append ? "wa" : "w";
            OutputStream output =
                    context.getContentResolver().openOutputStream(target.uri(), mode);
            if (output == null) {
                return FileResult.failure(
                        FileFailureCode.IO_ERROR, "provider returned null stream");
            }
            return FileResult.ok(output);
        } catch (IOException | RuntimeException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot open: " + target.uri(), e);
        }
    }

    @NonNull
    public static FileResult<Void> writeText(@NonNull Context context,
                                             @NonNull UriTarget target,
                                             @NonNull String text,
                                             @NonNull Charset charset) {
        return writeBytes(context, target, text.getBytes(charset));
    }

    @NonNull
    public static FileResult<Void> delete(@NonNull Context context,
                                          @NonNull UriTarget target) {
        if (target.uriKind() != com.yovo.lib_common.system.file.model.UriKind.DOCUMENT) {
            return FileResult.failure(
                    FileFailureCode.UNSUPPORTED, "delete by URI only supports DOCUMENT");
        }
        Uri uri = target.uri();
        try {
            boolean deleted;
            if (DocumentsContract.isDocumentUri(context, uri)) {
                deleted = DocumentsContract.deleteDocument(context.getContentResolver(), uri);
            } else {
                deleted = context.getContentResolver().delete(uri, null, null) > 0;
            }
            return deleted
                    ? FileResult.ok(null)
                    : FileResult.failure(FileFailureCode.IO_ERROR, "cannot delete: " + uri);
        } catch (java.io.FileNotFoundException e) {
            return FileResult.failure(
                    FileFailureCode.NOT_FOUND, "not found: " + uri, e);
        } catch (RuntimeException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot delete: " + uri, e);
        }
    }

    /**
     * 复制到指定文件；超过 maxBytes 时删除半成品并返回失败。
     */
    @NonNull
    public static FileResult<Long> copyTo(@NonNull Context context,
                                          @NonNull UriTarget target,
                                          @NonNull File destination,
                                          long maxBytes,
                                          @NonNull ConflictPolicy conflict) {
        if (maxBytes == 0L || maxBytes < NO_SIZE_LIMIT) {
            return FileResult.failure(
                    FileFailureCode.INVALID_TARGET,
                    "maxBytes must be positive or NO_SIZE_LIMIT");
        }
        if (destination.exists() && conflict == ConflictPolicy.FAIL) {
            return FileResult.failure(
                    FileFailureCode.ALREADY_EXISTS, "destination exists: " + destination);
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            FileResult<File> ensured = PathFileStore.ensureDir(parent);
            if (!ensured.success()) {
                return FileResult.failure(ensured.requireFailure());
            }
        }
        File temp = new File(parent != null ? parent : new File("."),
                destination.getName() + ".tmp-" + System.currentTimeMillis());
        long total = 0L;
        try (InputStream input = context.getContentResolver().openInputStream(target.uri());
             FileOutputStream output = new FileOutputStream(temp)) {
            if (input == null) {
                IoUtil.deleteQuietly(temp);
                return FileResult.failure(
                        FileFailureCode.IO_ERROR,
                        "provider returned null stream: " + target.uri());
            }
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (maxBytes != NO_SIZE_LIMIT && total > maxBytes) {
                    IoUtil.deleteQuietly(temp);
                    return FileResult.failure(
                            FileFailureCode.SIZE_LIMIT_EXCEEDED,
                            "URI content exceeds limit: " + maxBytes);
                }
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (IOException | RuntimeException e) {
            IoUtil.deleteQuietly(temp);
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot copy: " + target.uri(), e);
        }
        if (!IoUtil.moveReplace(temp, destination)) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR,
                    "cannot replace destination: " + destination);
        }
        return FileResult.ok(total);
    }

    /**
     * 复制到目录；使用 Provider 文件名或 preferredName，冲突按策略处理。
     */
    @NonNull
    public static FileResult<File> copyInto(@NonNull Context context,
                                            @NonNull UriTarget target,
                                            @NonNull File destinationDir,
                                            @Nullable String preferredName,
                                            long maxBytes,
                                            @NonNull ConflictPolicy conflict) {
        FileResult<UriFileInfo> info = inspect(context, target);
        String baseName = preferredName != null && !preferredName.trim().isEmpty()
                ? FileNames.sanitize(preferredName, "imported")
                : (info.success() ? info.value().displayName() : "imported");
        if (!destinationDir.isDirectory()) {
            FileResult<File> ensured = PathFileStore.ensureDir(destinationDir);
            if (!ensured.success()) {
                return FileResult.failure(ensured.requireFailure());
            }
        }
        File destination = new File(destinationDir, baseName);
        if (destination.exists() && conflict == ConflictPolicy.RENAME_UNIQUE) {
            destination = new File(
                    destinationDir,
                    PathFileStore.uniqueName(destinationDir, baseName));
        }
        FileResult<Long> copied =
                copyTo(context, target, destination, maxBytes, conflict);
        if (!copied.success()) {
            return FileResult.failure(copied.requireFailure());
        }
        return FileResult.ok(destination);
    }

    private static boolean documentSupportsWrite(@NonNull Context context,
                                                 @NonNull Uri uri) {
        try {
            if (!DocumentsContract.isDocumentUri(context, uri)) {
                return false;
            }
            Cursor cursor = context.getContentResolver().query(
                    uri,
                    new String[]{DocumentsContract.Document.COLUMN_FLAGS},
                    null,
                    null,
                    null);
            if (cursor == null) {
                return false;
            }
            try {
                if (!cursor.moveToFirst()) {
                    return false;
                }
                int index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS);
                return index >= 0 && !cursor.isNull(index)
                        && (cursor.getInt(index)
                        & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0;
            } finally {
                cursor.close();
            }
        } catch (RuntimeException e) {
            return false;
        }
    }
}
