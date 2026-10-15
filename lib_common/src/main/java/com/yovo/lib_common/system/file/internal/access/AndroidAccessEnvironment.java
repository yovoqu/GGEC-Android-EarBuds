package com.yovo.lib_common.system.file.internal.access;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.yovo.lib_common.system.file.internal.resolve.AndroidPathRootProvider;
import com.yovo.lib_common.system.file.internal.saf.android.SafPermissionChecker;
import com.yovo.lib_common.system.file.model.PathTarget;
import com.yovo.lib_common.system.permission.YoPermission;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Context + YoPermission 的访问环境实现。
 */
public final class AndroidAccessEnvironment implements AccessEnvironment {

    private final Context appContext;
    private final AndroidPathRootProvider rootProvider;

    public AndroidAccessEnvironment(@NonNull Context context) {
        appContext = context.getApplicationContext();
        rootProvider = new AndroidPathRootProvider(appContext);
    }

    @Override
    public boolean isStorageDirectGranted() {
        return missingStorageDirectPermissions().isEmpty();
    }

    @Override
    @NonNull
    public List<Permission> missingStorageDirectPermissions() {
        List<Permission> missing = new ArrayList<>();
        for (Permission permission :
                PermissionPolicy.STORAGE_DIRECT.requiredFor(Build.VERSION.SDK_INT)) {
            if (!YoPermission.isGranted(appContext, permission)) {
                missing.add(permission);
            }
        }
        return missing;
    }

    @Override
    @Nullable
    public File externalRoot(@NonNull PathTarget target) {
        return rootProvider.root(target);
    }

    @Override
    public boolean hasPersistedReadPermission(@NonNull Uri uri) {
        return SafPermissionChecker.hasReadPermission(appContext, uri);
    }

    @Override
    public boolean hasPersistedWritePermission(@NonNull Uri uri) {
        return SafPermissionChecker.hasWritePermission(appContext, uri);
    }

    @Override
    public boolean isDocumentUri(@NonNull Uri uri) {
        try {
            return DocumentsContract.isDocumentUri(appContext, uri);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean documentSupportsWrite(@NonNull Uri uri) {
        try {
            Cursor cursor = appContext.getContentResolver().query(
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
                if (index < 0 || cursor.isNull(index)) {
                    return false;
                }
                return (cursor.getInt(index)
                        & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0;
            } finally {
                cursor.close();
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean isTreeDirectory(@NonNull Uri treeUri) {
        try {
            DocumentFile tree = DocumentFile.fromTreeUri(appContext, treeUri);
            return tree != null && tree.isDirectory();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean canOpenDocument(@NonNull Uri uri, boolean write) {
        // 使用 checkUriPermission，禁止 openFileDescriptor("w")：
        // 平台 "w"/"wt" 会 truncate，check-only 门禁绝不能清空文件。
        int modeFlags = write
                ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                : Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            return appContext.checkUriPermission(
                    uri, Process.myPid(), Process.myUid(), modeFlags)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
