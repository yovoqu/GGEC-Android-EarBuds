package com.yovo.lib_common.system.file.internal.access;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.file.model.AccessCheckResult;
import com.yovo.lib_common.system.file.model.AccessDenyReason;
import com.yovo.lib_common.system.file.model.AccessMode;
import com.yovo.lib_common.system.file.model.FileScope;
import com.yovo.lib_common.system.file.model.FileTarget;
import com.yovo.lib_common.system.file.model.PathTarget;
import com.yovo.lib_common.system.file.model.UriKind;
import com.yovo.lib_common.system.file.model.UriTarget;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;

import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * 访问门禁：check-only，不 request、不弹 UI。
 */
public final class AccessGate {

    private AccessGate() {
    }

    @NonNull
    public static AccessCheckResult check(@NonNull FileTarget target,
                                          @NonNull AccessMode mode,
                                          @NonNull AccessEnvironment environment) {
        if (target.isPath()) {
            return checkPath(target.asPath(), environment);
        }
        return checkUri(target.asUri(), mode, environment);
    }

    @NonNull
    public static AccessCheckResult checkPath(@NonNull PathTarget target,
                                              @NonNull AccessEnvironment environment) {
        if (target.scope() == FileScope.APP_INTERNAL) {
            return AccessCheckResult.allowed();
        }
        if (target.scope() == FileScope.APP_EXTERNAL) {
            if (environment.externalRoot(target) == null) {
                return AccessCheckResult.denied(AccessDenyReason.EXTERNAL_MEDIA_UNAVAILABLE);
            }
            return AccessCheckResult.allowed();
        }
        if (!environment.isStorageDirectGranted()) {
            return AccessCheckResult.denied(
                    AccessDenyReason.MISSING_STORAGE_DIRECT,
                    environment.missingStorageDirectPermissions());
        }
        return AccessCheckResult.allowed();
    }

    @NonNull
    public static AccessCheckResult checkUri(@NonNull UriTarget target,
                                             @NonNull AccessMode mode,
                                             @NonNull AccessEnvironment environment) {
        Uri uri = target.uri();
        boolean needsRead = mode != AccessMode.WRITE;
        boolean needsWrite = mode != AccessMode.READ;

        if (target.uriKind() == UriKind.TREE) {
            boolean treeOpen = environment.isTreeDirectory(uri);
            if (!treeOpen) {
                return uriPermissionDenied(environment, uri);
            }
            // TREE 也必须按 AccessMode 校验读写授权（临时只读 grant 不得放过 WRITE）。
            boolean readOk = !needsRead
                    || environment.hasPersistedReadPermission(uri)
                    || environment.canOpenDocument(uri, false);
            boolean writeOk = !needsWrite
                    || environment.hasPersistedWritePermission(uri)
                    || environment.canOpenDocument(uri, true);
            if (!readOk || !writeOk) {
                return uriPermissionDenied(environment, uri);
            }
            return AccessCheckResult.allowed();
        }

        if (target.relativePath() != null) {
            return AccessCheckResult.denied(AccessDenyReason.INVALID_TARGET);
        }
        if (needsWrite && environment.isDocumentUri(uri)
                && !environment.documentSupportsWrite(uri)) {
            return AccessCheckResult.denied(AccessDenyReason.SAF_NOT_WRITABLE);
        }
        boolean readOk = !needsRead
                || environment.hasPersistedReadPermission(uri)
                || environment.canOpenDocument(uri, false);
        boolean writeOk = !needsWrite
                || environment.hasPersistedWritePermission(uri)
                || environment.canOpenDocument(uri, true);
        if (!readOk || !writeOk) {
            return uriPermissionDenied(environment, uri);
        }
        return AccessCheckResult.allowed();
    }

    @NonNull
    private static AccessCheckResult uriPermissionDenied(
            @NonNull AccessEnvironment environment,
            @NonNull Uri uri) {
        boolean any =
                environment.hasPersistedReadPermission(uri)
                        || environment.hasPersistedWritePermission(uri);
        return AccessCheckResult.denied(
                any ? AccessDenyReason.URI_PERMISSION_LOST
                        : AccessDenyReason.URI_PERMISSION_MISSING);
    }
}
