package com.ggec.earbuds.provider.lib_common.permission;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.permission.YoPermission;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;
import com.yovo.lib_common.system.permission.model.PermissionResult;

import java.util.List;

/**
 * App 侧权限 Provider（按需封装 {@code lib_common} 权限域）。
 *
 * <p>只暴露 App 真正需要的两类能力：蓝牙扫描权限请求、权限回调结果归一。</p>
 */
public final class PermissionProvider {

    private PermissionProvider() {
    }

    /** 权限回调结果的精简投影（不向 UI 暴露 lib_common 类型）。 */
    public static final class Result {
        private final boolean handled;
        private final boolean allGranted;
        private final boolean permanentlyDenied;

        Result(boolean handled, boolean allGranted, boolean permanentlyDenied) {
            this.handled = handled;
            this.allGranted = allGranted;
            this.permanentlyDenied = permanentlyDenied;
        }

        public boolean handled() {
            return handled;
        }

        public boolean allGranted() {
            return allGranted;
        }

        public boolean permanentlyDenied() {
            return permanentlyDenied;
        }
    }

    /** 蓝牙扫描权限当前是否已全部授予（只查询，不弹窗）。 */
    public static boolean isBluetoothGranted(@NonNull Context context) {
        return YoPermission.isGranted(context, PermissionPolicy.BLUETOOTH_FULL);
    }

    /** 处理 onRequestPermissionsResult：归一为 {@link Result}。 */
    @NonNull
    public static Result onRequestResult(@NonNull Activity activity,
                                         int requestCode,
                                         @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        PermissionResult result = YoPermission.handleRequestPermissionsResult(
                activity, requestCode, permissions, grantResults);
        return new Result(result.handled(), result.allGranted(), result.hasPermanentlyDenied());
    }

    /**
     * 请求蓝牙全量权限（按当前 SDK 版本）。
     *
     * @return true 表示已授权（无需弹窗或已授予），可直接开始扫描
     */
    public static boolean requestBluetoothIfNeeded(@NonNull Activity activity) {
        List<Permission> required = PermissionPolicy.BLUETOOTH_FULL.requiredFor(Build.VERSION.SDK_INT);
        return YoPermission.requestIfNeeded(activity, required);
    }
}
