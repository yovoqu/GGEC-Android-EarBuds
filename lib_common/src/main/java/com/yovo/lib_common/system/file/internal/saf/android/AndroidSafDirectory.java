package com.yovo.lib_common.system.file.internal.saf.android;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.SafDirectory;
import com.yovo.lib_common.system.file.model.SafFile;
import com.yovo.lib_common.log.YoLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * SAF 目录树实现（DocumentFile 包装），所有平台 API 收敛在本类。
 */
public final class AndroidSafDirectory implements SafDirectory {

    private static final String TAG = "AndroidSafDirectory";

    private final Context appContext;
    private final Uri treeUri;
    private final DocumentFile tree;

    public AndroidSafDirectory(@NonNull Context appContext,
                               @NonNull Uri treeUri,
                               @NonNull DocumentFile tree) {
        this.appContext = appContext;
        this.treeUri = treeUri;
        this.tree = tree;
    }

    @NonNull
    @Override
    public Uri uri() {
        return treeUri;
    }

    @NonNull
    @Override
    public List<SafFile> listFiles() {
        return listFiles(name -> true);
    }

    @NonNull
    @Override
    public List<SafFile> listFiles(@NonNull Predicate<String> nameFilter) {
        List<SafFile> result = new ArrayList<>();
        try {
            for (DocumentFile child : tree.listFiles()) {
                String name = child.getName();
                if (name != null && nameFilter.test(name)) {
                    result.add(new AndroidSafFile(appContext, child));
                }
            }
        } catch (RuntimeException e) {
            YoLog.w(TAG, "列出文件失败：" + treeUri, e);
            return result;
        }
        return result;
    }

    @Nullable
    @Override
    public SafFile findFile(@NonNull String name) {
        try {
            DocumentFile child = tree.findFile(name);
            return child != null ? new AndroidSafFile(appContext, child) : null;
        } catch (RuntimeException e) {
            YoLog.w(TAG, "查找文件失败：" + treeUri, e);
            return null;
        }
    }

    @Nullable
    @Override
    public SafFile createFile(@NonNull String name, @NonNull String mimeType) {
        try {
            DocumentFile created = tree.createFile(mimeType, name);
            return created != null ? new AndroidSafFile(appContext, created) : null;
        } catch (RuntimeException e) {
            YoLog.w(TAG, "创建文件失败：" + treeUri, e);
            return null;
        }
    }

    @Override
    public boolean deleteFile(@NonNull String name) {
        try {
            DocumentFile child = tree.findFile(name);
            return child != null && child.delete();
        } catch (RuntimeException e) {
            YoLog.w(TAG, "删除文件失败：" + treeUri, e);
            return false;
        }
    }

    @Nullable
    @Override
    public SafFile copyFrom(@NonNull File source,
                            @NonNull String name,
                            @NonNull String mimeType) {
        return copyFrom(source, name, mimeType, ConflictPolicy.OVERWRITE,
                com.yovo.lib_common.system.file.internal.path.PathFileStore.NO_SIZE_LIMIT);
    }

    /**
     * 带冲突策略的复制；RENAME_UNIQUE 由调用方先解析唯一名。
     */
    @Nullable
    public SafFile copyFrom(@NonNull File source,
                            @NonNull String name,
                            @NonNull String mimeType,
                            @NonNull ConflictPolicy conflict) {
        return copyFrom(source, name, mimeType, conflict,
                com.yovo.lib_common.system.file.internal.path.PathFileStore.NO_SIZE_LIMIT);
    }

    /**
     * 带冲突策略与大小上限的复制；超过上限时清理半成品并返回 null。
     */
    @Nullable
    public SafFile copyFrom(@NonNull File source,
                            @NonNull String name,
                            @NonNull String mimeType,
                            @NonNull ConflictPolicy conflict,
                            long maxBytes) {
        try {
            if (conflict == ConflictPolicy.FAIL && tree.findFile(name) != null) {
                return null;
            }
            return copyFromInternal(source, name, mimeType, maxBytes);
        } catch (RuntimeException e) {
            YoLog.w(TAG, "复制文件失败：" + treeUri, e);
            return null;
        }
    }

    @Nullable
    private SafFile copyFromInternal(@NonNull File source,
                                     @NonNull String name,
                                     @NonNull String mimeType,
                                     long maxBytes) {
        DocumentFile existing = tree.findFile(name);
        DocumentFile target = tree.createFile(mimeType,
                name + ".tmp-" + System.currentTimeMillis());
        if (target == null) {
            return null;
        }
        long total = 0L;
        try (InputStream in = new FileInputStream(source)) {
            OutputStream out = appContext.getContentResolver()
                    .openOutputStream(target.getUri());
            if (out == null) {
                target.delete();
                return null;
            }
            try (OutputStream ignored = out) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (maxBytes != com.yovo.lib_common.system.file.internal.path.PathFileStore.NO_SIZE_LIMIT
                            && total > maxBytes) {
                        target.delete();
                        return null;
                    }
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
        } catch (IOException e) {
            target.delete();
            return null;
        }

        // 备份式替换：旧文件先改名保留，新文件改名成功后再删备份，
        // 任一步失败都可回滚，避免覆盖写丢失原文件。
        String backupName = name + ".bak-" + System.currentTimeMillis();
        if (existing != null && !existing.renameTo(backupName)) {
            target.delete();
            return null;
        }
        if (!target.renameTo(name)) {
            target.delete();
            if (existing != null) {
                //noinspection ResultOfMethodCallIgnored
                existing.renameTo(name);
            }
            return null;
        }
        if (existing != null) {
            //noinspection ResultOfMethodCallIgnored
            existing.delete();
        }
        return new AndroidSafFile(appContext, target);
    }
}
