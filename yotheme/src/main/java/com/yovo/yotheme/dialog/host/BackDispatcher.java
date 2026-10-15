package com.yovo.yotheme.dialog.host;

import android.app.Activity;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;
import com.yovo.yotheme.dialog.core.DialogInstance;
import com.yovo.yotheme.dialog.core.DialogRegistry;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 将返回键事件路由至 Activity 顶部对话框
 */
public final class BackDispatcher {

    @NonNull
    private final Activity activity;

    @NonNull
    private final DialogRegistry registry;

    @Nullable
    private OnBackPressedCallback callback;

    public BackDispatcher(@NonNull Activity activity, @NonNull DialogRegistry registry) {
        this.activity = activity;
        this.registry = registry;
    }

    public void install() {
        if (!(activity instanceof androidx.activity.ComponentActivity)) {
            return;
        }
        if (callback != null) {
            return;
        }
        callback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        };
        androidx.activity.ComponentActivity componentActivity =
                (androidx.activity.ComponentActivity) activity;
        componentActivity.getOnBackPressedDispatcher()
                .addCallback(componentActivity, callback);
        refreshEnabled();
    }

    public void uninstall() {
        if (callback != null) {
            callback.remove();
            callback = null;
        }
    }

    public void refreshEnabled() {
        if (callback == null) {
            return;
        }
        DialogInstance top = registry.top(activity);
        callback.setEnabled(top != null);
    }

    private void handleBack() {
        DialogInstance top = registry.top(activity);
        if (top == null) {
            return;
        }
        if (!top.getSpec().getBehavior().isCancelableByBack()) {
            return;
        }
        top.dismiss(DialogResult.Reason.BACK);
    }
}

