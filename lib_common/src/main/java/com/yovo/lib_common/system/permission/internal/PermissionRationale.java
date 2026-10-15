package com.yovo.lib_common.system.permission.internal;

import com.yovo.lib_common.system.permission.model.Permission;

import android.app.Activity;

import androidx.annotation.NonNull;

/**
 * 运行时权限“为什么需要该权限”的说明判定端口。
 *
 * <p>用于区分普通拒绝与“不再询问”：
 * 拒绝后 shouldShowRequestPermissionRationale 返回 false 时，
 * 系统不再弹出权限框，需要引导用户前往设置页手动开启。</p>
 */
public interface PermissionRationale {

    boolean shouldShow(@NonNull Activity activity, @NonNull Permission permission);
}
