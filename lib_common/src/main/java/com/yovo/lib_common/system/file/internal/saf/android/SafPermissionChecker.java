package com.yovo.lib_common.system.file.internal.saf.android;

import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SAF 持久化 URI 授权判定（仅基础设施层使用）。
 */
public final class SafPermissionChecker {

    private SafPermissionChecker() {
    }

    public static boolean hasReadWritePermission(@NonNull Context context,
                                                 @Nullable Uri uri) {
        return hasPermission(context, uri, true, true);
    }

    public static boolean hasReadPermission(@NonNull Context context,
                                            @Nullable Uri uri) {
        return hasPermission(context, uri, true, false);
    }

    public static boolean hasWritePermission(@NonNull Context context,
                                             @Nullable Uri uri) {
        return hasPermission(context, uri, false, true);
    }

    @NonNull
    public static List<Uri> persistedUris(@NonNull Context context) {
        List<Uri> result = new ArrayList<>();
        for (UriPermission permission :
                context.getContentResolver().getPersistedUriPermissions()) {
            result.add(permission.getUri());
        }
        return result;
    }

    private static boolean hasPermission(@NonNull Context context,
                                         @Nullable Uri uri,
                                         boolean requireRead,
                                         boolean requireWrite) {
        if (uri == null) {
            return false;
        }
        List<UriPermission> permissions =
                context.getContentResolver().getPersistedUriPermissions();
        for (UriPermission permission : permissions) {
            if (permission.getUri().equals(uri)
                    && (!requireRead || permission.isReadPermission())
                    && (!requireWrite || permission.isWritePermission())) {
                return true;
            }
        }
        return false;
    }
}
