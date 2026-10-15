package com.yovo.lib_common.system.permission.internal.android;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.internal.core.PermissionCore;
import com.yovo.lib_common.system.permission.model.PermissionStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 特殊访问权限设置页回程监听：
 * 拉起设置页后 Activity 会经历 PAUSE → RESUME，在 RESUME 时校验授权结果并回调一次。
 */
public final class SpecialAccessLifecycleObserver implements LifecycleEventObserver {

    private final Activity activity;
    private final List<Permission> permissions;
    private final PermissionCore.SpecialAccessCallback callback;
    private boolean called;

    private SpecialAccessLifecycleObserver(
            @NonNull Activity activity,
            @NonNull List<Permission> permissions,
            @NonNull PermissionCore.SpecialAccessCallback callback) {
        this.activity = activity;
        this.permissions = permissions;
        this.callback = callback;
    }

    public static void observe(@NonNull Activity activity,
                               @NonNull List<Permission> permissions,
                               @NonNull PermissionCore.SpecialAccessCallback callback) {
        if (!(activity instanceof LifecycleOwner)) {
            throw new IllegalArgumentException(
                    "requestSpecialAccess 需要 LifecycleOwner Activity");
        }
        SpecialAccessLifecycleObserver observer =
                new SpecialAccessLifecycleObserver(activity, permissions, callback);
        ((LifecycleOwner) activity).getLifecycle().addObserver(observer);
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source,
                               @NonNull Lifecycle.Event event) {
        if (event != Lifecycle.Event.ON_RESUME
                && event != Lifecycle.Event.ON_DESTROY) {
            return;
        }
        source.getLifecycle().removeObserver(this);
        if (called) {
            return;
        }
        called = true;
        PermissionCore manager = PermissionCore.with(activity);
        List<Permission> denied = new ArrayList<>();
        boolean allGranted = true;
        for (Permission permission : permissions) {
            if (manager.check(permission) != PermissionStatus.GRANTED) {
                denied.add(permission);
                allGranted = false;
            }
        }
        callback.onResult(allGranted, denied);
    }
}
