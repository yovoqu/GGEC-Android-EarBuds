package com.yovo.yotheme.dialog.host;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;
import com.yovo.yotheme.dialog.core.DialogRegistry;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 所属 Activity 销毁时关闭全部对话框
 */
public final class OwnerLifecycleObserver implements DefaultLifecycleObserver {

    @NonNull
    private final Activity activity;

    @NonNull
    private final DialogRegistry registry;

    public OwnerLifecycleObserver(@NonNull Activity activity, @NonNull DialogRegistry registry) {
        this.activity = activity;
        this.registry = registry;
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        registry.dismissAll(activity, DialogResult.Reason.LIFECYCLE);
    }
}
