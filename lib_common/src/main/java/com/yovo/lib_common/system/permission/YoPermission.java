package com.yovo.lib_common.system.permission;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.permission.internal.android.AndroidPermissionChecker;
import com.yovo.lib_common.system.permission.internal.android.AndroidPermissionRationale;
import com.yovo.lib_common.system.permission.internal.android.AndroidPermissionRequester;
import com.yovo.lib_common.system.permission.internal.core.PermissionCore;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;
import com.yovo.lib_common.system.permission.model.PermissionResult;
import com.yovo.lib_common.system.permission.model.PermissionStatus;

import android.os.Build;

import java.util.List;

/**
 * 权限域唯一对外静态门面。
 *
 * <p>平台实现（checker/requester/rationale）在门面构造时显式注入唯一实例，
 * 该实例持有 pending 表；不再有静态可写的单例字段。</p>
 */
public final class YoPermission {

    /** 默认运行时权限组请求码。 */
    public static final int REQUEST_CODE_GROUP = 0xB0F0;

    private static final PermissionCore CORE = PermissionCore.create(
            new AndroidPermissionChecker(),
            new AndroidPermissionRequester(),
            new AndroidPermissionRationale());

    private YoPermission() {
    }

    @NonNull
    public static PermissionStatus check(@NonNull Context context,
                                         @NonNull Permission permission) {
        return CORE.check(context, permission);
    }

    public static boolean isGranted(@NonNull Context context,
                                    @NonNull Permission permission) {
        return CORE.isGranted(context, permission);
    }

    public static boolean isGranted(@NonNull Context context,
                                    @NonNull List<Permission> permissions) {
        return CORE.isGranted(context, permissions);
    }

    public static boolean isGranted(@NonNull Context context,
                                    @NonNull PermissionPolicy policy) {
        return isGranted(context, policy.requiredFor(Build.VERSION.SDK_INT));
    }

    public static boolean shouldShowRationale(@NonNull Activity activity,
                                              @NonNull Permission permission) {
        return CORE.shouldShowRationale(activity, permission);
    }

    public static boolean requestIfNeeded(@NonNull Activity activity,
                                          @NonNull Permission permission) {
        return CORE.requestIfNeeded(activity, permission);
    }

    public static boolean requestIfNeeded(@NonNull Activity activity,
                                          @NonNull Permission permission,
                                          int requestCode) {
        return CORE.requestIfNeeded(activity, permission, requestCode);
    }

    public static boolean requestIfNeeded(@NonNull Activity activity,
                                          @NonNull List<Permission> permissions) {
        return CORE.requestIfNeeded(activity, permissions);
    }

    public static boolean requestIfNeeded(@NonNull Activity activity,
                                          @NonNull List<Permission> permissions,
                                          int requestCode) {
        return CORE.requestIfNeeded(activity, permissions, requestCode);
    }

    public static void requestSpecialAccess(@NonNull Activity activity,
                                            @NonNull List<Permission> permissions,
                                            @NonNull SpecialAccessCallback callback) {
        CORE.requestSpecialAccess(
                activity, permissions, new PermissionCore.SpecialAccessCallback() {
                    @Override
                    public void onResult(boolean allGranted,
                                         @NonNull List<Permission> denied) {
                        callback.onResult(allGranted, denied);
                    }
                });
    }

    public static void openAppSettings(@NonNull Activity activity) {
        CORE.openAppSettings(activity);
    }

    /**
     * 处理「没有 Activity 上下文」的权限回调。
     *
     * <p>返回值直接透传核心分类结果：未命中时 {@link PermissionResult#ignoreReason()}
     * 区分「没有待处理请求」「requestCode 未知」「多 Activity 撞码」等原因。</p>
     */
    @NonNull
    public static PermissionResult handleRequestPermissionsResult(
            int requestCode,
            @Nullable String[] permissions,
            @Nullable int[] grantResults) {
        return CORE.handleRequestPermissionsResult(
                null, requestCode, permissions, grantResults);
    }

    /**
     * 处理 Activity#onRequestPermissionsResult，并透传核心的忽略原因分类。
     *
     * @param activity 发起请求的 Activity；为 {@code null} 时只能处理全局唯一命中的请求
     */
    @NonNull
    public static PermissionResult handleRequestPermissionsResult(
            @Nullable Activity activity,
            int requestCode,
            @Nullable String[] permissions,
            @Nullable int[] grantResults) {
        return CORE.handleRequestPermissionsResult(
                activity, requestCode, permissions, grantResults);
    }

    public interface SpecialAccessCallback {
        void onResult(boolean allGranted, @NonNull List<Permission> denied);
    }
}
