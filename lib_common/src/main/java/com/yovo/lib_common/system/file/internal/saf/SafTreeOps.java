package com.yovo.lib_common.system.file.internal.saf;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.yovo.lib_common.internal.util.FileNames;
import com.yovo.lib_common.system.file.internal.path.PathFileStore;
import com.yovo.lib_common.system.file.internal.saf.android.AndroidSafDirectory;
import com.yovo.lib_common.system.file.internal.saf.android.SafPermissionChecker;
import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.FileEntry;
import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.SafDirectory;
import com.yovo.lib_common.system.file.model.SafFile;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.yovo.lib_common.system.file.model.OpenOptions;

/**
 * SAF 目录树操作：授权判定、打开、导航、列表、复制、删除、persistable 管理。
 */
public final class SafTreeOps {

    private SafTreeOps() {
    }

    public static boolean isTreeAccessible(@NonNull Context context,
                                           @Nullable Uri treeUri) {
        return treeUri != null && treeOpen(context, treeUri);
    }

    @Nullable
    public static SafDirectory openTree(@NonNull Context context,
                                        @NonNull Uri treeUri) {
        return openTree(context, treeUri, null);
    }

    @Nullable
    public static SafDirectory openTree(@NonNull Context context,
                                        @NonNull Uri treeUri,
                                        @Nullable String relativePath) {
        return openTreeImpl(context, treeUri, relativePath);
    }

    @Nullable
    private static AndroidSafDirectory openTreeImpl(@NonNull Context context,
                                                    @NonNull Uri treeUri,
                                                    @Nullable String relativePath) {
        if (relativePath == null && !treeOpen(context, treeUri)) {
            return null;
        }
        DocumentFile document = openDocumentTree(context, treeUri, relativePath);
        if (document == null || !document.isDirectory()) {
            return null;
        }
        return new AndroidSafDirectory(
                context.getApplicationContext(), document.getUri(), document);
    }

    /** 树/子路径是否真实可打开（持久化或临时授权均可）。 */
    public static boolean pathExists(@NonNull Context context,
                                     @NonNull Uri treeUri,
                                     @Nullable String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return treeOpen(context, treeUri);
        }
        return openDocumentTree(context, treeUri, relativePath) != null;
    }

    private static boolean treeOpen(@NonNull Context context,
                                    @NonNull Uri treeUri) {
        try {
            DocumentFile tree = DocumentFile.fromTreeUri(context, treeUri);
            return tree != null && tree.isDirectory();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @NonNull
    public static FileResult<List<FileEntry>> listTree(
            @NonNull Context context,
            @NonNull Uri treeUri,
            @Nullable String relativePath,
            @Nullable Predicate<FileEntry> filter) {
        SafDirectory directory = openTree(context, treeUri, relativePath);
        if (directory == null) {
            return FileResult.failure(
                    FileFailureCode.DENIED,
                    "tree unavailable: " + treeUri);
        }
        List<FileEntry> result = new ArrayList<>();
        for (SafFile file : directory.listFiles()) {
            FileEntry entry = new FileEntry(
                    file.name(), null, null, file.length(), file.lastModified(),
                    file.isDirectory());
            if (filter == null || filter.test(entry)) {
                result.add(entry);
            }
        }
        return FileResult.ok(result);
    }

    @NonNull
    public static FileResult<List<FileEntry>> listTreeRecursive(
            @NonNull Context context,
            @NonNull Uri treeUri,
            @Nullable String relativePath,
            @Nullable Predicate<FileEntry> filter) {
        DocumentFile root = openDocumentTree(context, treeUri, relativePath);
        if (root == null || !root.isDirectory()) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "tree unavailable: " + treeUri);
        }
        List<FileEntry> result = new ArrayList<>();
        FileResult<Void> walk = walkTree(context, root, filter, result, 0);
        if (!walk.success()) {
            return FileResult.failure(walk.requireFailure());
        }
        return FileResult.ok(result);
    }

    @NonNull
    private static FileResult<Void> walkTree(@NonNull Context context,
                                             @NonNull DocumentFile directory,
                                             @Nullable Predicate<FileEntry> filter,
                                             @NonNull List<FileEntry> sink,
                                             int depth) {
        if (depth > 32) {
            return FileResult.failure(FileFailureCode.IO_ERROR, "tree recursion too deep");
        }
        DocumentFile[] children;
        try {
            children = directory.listFiles();
        } catch (RuntimeException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "list tree failed", e);
        }
        if (children == null) {
            return FileResult.ok(null);
        }
        for (DocumentFile child : children) {
            String name = child.getName();
            if (name == null) {
                continue;
            }
            FileEntry entry = new FileEntry(
                    name, null, child.getUri(), child.length(),
                    child.lastModified(), child.isDirectory());
            if (filter == null || filter.test(entry)) {
                sink.add(entry);
            }
            if (child.isDirectory()) {
                FileResult<Void> sub = walkTree(
                        context, child, filter, sink, depth + 1);
                if (!sub.success()) {
                    return sub;
                }
            }
        }
        return FileResult.ok(null);
    }

    @NonNull
    public static FileResult<SafFile> copyFileToTree(
            @NonNull Context context,
            @NonNull Uri treeUri,
            @Nullable String relativePath,
            @NonNull File source,
            @NonNull String name,
            @NonNull String mimeType,
            long maxBytes,
            @NonNull ConflictPolicy conflict) {
        AndroidSafDirectory directory = openTreeImpl(context, treeUri, relativePath);
        if (directory == null) {
            return FileResult.failure(
                    FileFailureCode.DENIED,
                    "tree unavailable: " + treeUri);
        }
        if (maxBytes != PathFileStore.NO_SIZE_LIMIT && source.length() > maxBytes) {
            return FileResult.failure(
                    FileFailureCode.SIZE_LIMIT_EXCEEDED,
                    "source exceeds limit: " + maxBytes + " bytes");
        }
        String safeName = FileNames.sanitize(name, source.getName());
        if (conflict == ConflictPolicy.RENAME_UNIQUE) {
            String base = safeName;
            int index = 1;
            while (directory.findFile(safeName) != null) {
                safeName = uniqueTreeName(base, index);
                index++;
            }
        } else if (conflict == ConflictPolicy.FAIL
                && directory.findFile(safeName) != null) {
            return FileResult.failure(
                    FileFailureCode.ALREADY_EXISTS, "exists in tree: " + safeName);
        }
        SafFile created = directory.copyFrom(
                source, safeName, mimeType, conflict, maxBytes);
        if (created == null) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "copy to tree failed: " + safeName);
        }
        return FileResult.ok(created);
    }

    @NonNull
    private static String uniqueTreeName(@NonNull String baseName, int index) {
        String name = baseName;
        String extension = "";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0 && dot < baseName.length() - 1) {
            name = baseName.substring(0, dot);
            extension = baseName.substring(dot);
        }
        return name + "(" + index + ")" + extension;
    }

    @NonNull
    public static FileResult<Void> deleteEntry(@NonNull Context context,
                                               @NonNull Uri treeUri,
                                               @Nullable String relativePath,
                                               @NonNull String name) {
        SafDirectory directory = openTree(context, treeUri, relativePath);
        if (directory == null) {
            return FileResult.failure(
                    FileFailureCode.DENIED,
                    "tree unavailable: " + treeUri);
        }
        if (directory.findFile(name) == null) {
            return FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + name);
        }
        return directory.deleteFile(name)
                ? FileResult.ok(null)
                : FileResult.failure(FileFailureCode.IO_ERROR, "cannot delete: " + name);
    }

    @NonNull
    public static FileResult<Void> deletePath(@NonNull Context context,
                                              @NonNull Uri treeUri,
                                              @NonNull String relativePath) {
        DocumentFile document = openDocumentTree(context, treeUri, relativePath);
        if (document == null) {
            return FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + relativePath);
        }
        return document.delete()
                ? FileResult.ok(null)
                : FileResult.failure(
                FileFailureCode.IO_ERROR, "cannot delete: " + relativePath);
    }

    @NonNull
    public static FileResult<InputStream> openInputStream(
            @NonNull Context context,
            @NonNull Uri treeUri,
            @NonNull String relativePath) {
        DocumentFile document = openDocumentTree(context, treeUri, relativePath);
        if (document == null || !document.isFile()) {
            return FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + relativePath);
        }
        try {
            InputStream input =
                    context.getContentResolver().openInputStream(document.getUri());
            if (input == null) {
                return FileResult.failure(
                        FileFailureCode.IO_ERROR, "provider returned null stream");
            }
            return FileResult.ok(input);
        } catch (IOException | RuntimeException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot open: " + relativePath, e);
        }
    }

    @NonNull
    public static FileResult<OutputStream> openOutputStream(
            @NonNull Context context,
            @NonNull Uri treeUri,
            @NonNull String relativePath,
            @NonNull Set<OpenOptions> options) {
        DocumentFile document = openDocumentTree(context, treeUri, relativePath);
        if (document == null) {
            return FileResult.failure(FileFailureCode.NOT_FOUND, "not found: " + relativePath);
        }
        if (options.contains(OpenOptions.CREATE_NEW) && document.exists()) {
            return FileResult.failure(
                    FileFailureCode.ALREADY_EXISTS, "already exists: " + relativePath);
        }
        try {
            String mode = options.contains(OpenOptions.APPEND) ? "wa" : "w";
            OutputStream output =
                    context.getContentResolver().openOutputStream(document.getUri(), mode);
            if (output == null) {
                return FileResult.failure(
                        FileFailureCode.IO_ERROR, "provider returned null stream");
            }
            return FileResult.ok(output);
        } catch (IOException | RuntimeException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR, "cannot open: " + relativePath, e);
        }
    }

    @NonNull
    public static FileResult<Void> takePersistable(@NonNull Context context,
                                                   @NonNull Uri treeUri,
                                                   int modeFlags) {
        try {
            context.getContentResolver().takePersistableUriPermission(treeUri, modeFlags);
            return FileResult.ok(null);
        } catch (SecurityException | IllegalArgumentException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot take persistable permission", e);
        }
    }

    @NonNull
    public static FileResult<Void> releasePersistable(@NonNull Context context,
                                                      @NonNull Uri treeUri,
                                                      int modeFlags) {
        try {
            context.getContentResolver().releasePersistableUriPermission(treeUri, modeFlags);
            return FileResult.ok(null);
        } catch (SecurityException | IllegalArgumentException e) {
            return FileResult.failure(
                    FileFailureCode.DENIED, "cannot release persistable permission", e);
        }
    }

    @NonNull
    public static List<Uri> listPersisted(@NonNull Context context) {
        return SafPermissionChecker.persistedUris(context);
    }

    @NonNull
    public static Intent createTreePickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    @Nullable
    private static DocumentFile openDocumentTree(@NonNull Context context,
                                                 @NonNull Uri treeUri,
                                                 @Nullable String relativePath) {
        try {
            DocumentFile current = DocumentFile.fromTreeUri(context, treeUri);
            if (current == null) {
                return null;
            }
            if (relativePath == null || relativePath.isEmpty()) {
                return current;
            }
            for (String segment : relativePath.replace('\\', '/').split("/")) {
                if (segment.isEmpty() || ".".equals(segment)) {
                    continue;
                }
                if ("..".equals(segment)) {
                    return null;
                }
                current = current.findFile(segment);
                if (current == null) {
                    return null;
                }
            }
            return current;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
