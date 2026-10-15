package com.yovo.lib_common.system.file.internal.access;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.PathTarget;
import com.yovo.lib_common.system.permission.model.Permission;

import java.io.File;
import java.util.List;

/**
 * 访问门禁所需的平台能力（Android 实现与测试实现可注入）。
 */
public interface AccessEnvironment {

    /** STORAGE_DIRECT 策略要求的全部权限是否已授予。 */
    boolean isStorageDirectGranted();

    /** STORAGE_DIRECT 策略下仍缺失的权限。 */
    @NonNull
    List<Permission> missingStorageDirectPermissions();

    /** APP_EXTERNAL 根目录；介质不可用时返回 null。 */
    @Nullable
    File externalRoot(@NonNull PathTarget target);

    boolean hasPersistedReadPermission(@NonNull Uri uri);

    boolean hasPersistedWritePermission(@NonNull Uri uri);

    /** 是否为 DocumentsProvider 文档 URI。 */
    boolean isDocumentUri(@NonNull Uri uri);

    /** 文档 URI 的 COLUMN_FLAGS 是否含 FLAG_SUPPORTS_WRITE。 */
    boolean documentSupportsWrite(@NonNull Uri uri);

    /**
     * 探测对 URI 的临时/持久授权（check-only，不得打开会截断的写句柄）。
     *
     * @param write true 探测写授权，false 探测读授权
     */
    boolean canOpenDocument(@NonNull Uri uri, boolean write);

    /** 树 URI 是否可打开为目录。 */
    boolean isTreeDirectory(@NonNull Uri treeUri);
}
