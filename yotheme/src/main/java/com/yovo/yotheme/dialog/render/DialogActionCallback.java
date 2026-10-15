package com.yovo.yotheme.dialog.render;

import androidx.annotation.NonNull;

import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 将 {@link ButtonBarRenderer} 的按钮点击桥接至对话框生命周期
 */
public interface DialogActionCallback {

    void onAction(@NonNull DialogResult result);

    void requestDismiss(@NonNull DialogResult result);
}
