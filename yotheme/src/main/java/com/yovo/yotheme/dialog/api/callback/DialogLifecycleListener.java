package com.yovo.yotheme.dialog.api.callback;

import androidx.annotation.NonNull;

import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;
import com.yovo.yotheme.dialog.api.lifecycle.DialogState;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 对话框实例的生命周期回调
 */
public interface DialogLifecycleListener {

    default void onWillShow() {}

    default void onShown() {}

    default void onWillDismiss(@NonNull DialogResult result) {}

    default void onDismissed(@NonNull DialogResult result) {}

    default void onStateChanged(@NonNull DialogState state) {}
}

