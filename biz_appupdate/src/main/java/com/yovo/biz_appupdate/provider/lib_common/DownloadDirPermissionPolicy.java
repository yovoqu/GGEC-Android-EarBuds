package com.yovo.biz_appupdate.provider.lib_common;

import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;

import java.util.List;

/**
 * 下载目录 → lib_common 统一权限目录的映射策略（Android 10 ~ Android 17）。
 *
 * <ul>
 *     <li>应用包目录：任何版本都不需要权限；</li>
 *     <li>绝对目录：Android 10（API 29）→ WRITE_EXTERNAL_STORAGE；
 *     Android 11+（API 30+，含 Android 16/17）→ MANAGE_EXTERNAL_STORAGE。</li>
 *     <li>SAF 目录树：免存储权限，仅需系统文件选择器授权。</li>
 * </ul>
 *
 * <p>实际的检查与请求由 lib_common 的 {@link PermissionManager} 统一执行。</p>
 */
public final class DownloadDirPermissionPolicy {

    /** Android 10。 */
    public static final int ANDROID_10 = 29;
    /** Android 11（MANAGE_EXTERNAL_STORAGE 起始版本）。 */
    public static final int ANDROID_11 = 30;

    private DownloadDirPermissionPolicy() {
    }

    /**
     * @return 需要的统一权限；应用包目录或 Android 9- 返回 null（无需权限）。
     */
    @Nullable
    public static Permission requiredFor(int sdkInt, @NonNull DownloadDirSpec.Type type) {
        if (type == DownloadDirSpec.Type.APP_PRIVATE) {
            return null;
        }
        if (type == DownloadDirSpec.Type.SAF) {
            return null;
        }
        // 统一委托 lib_common 的“绝对目录写权限”策略，避免两处策略漂移。
        List<Permission> required = PermissionPolicy.STORAGE_DIRECT.requiredFor(sdkInt);
        return required.isEmpty() ? null : required.get(0);
    }
}
