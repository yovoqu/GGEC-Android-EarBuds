package com.yovo.yotheme.dialog.api.lifecycle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.api.callback.DialogLifecycleListener;

import java.util.function.Consumer;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description YoDialog show API 返回的公共句柄
 */
public interface DialogHandle {

    @NonNull
    String getId();

    @NonNull
    YoDialogSpec getSpec();

    @NonNull
    DialogState getState();

    boolean isShowing();

    void setLifecycleListener(@Nullable DialogLifecycleListener lifecycleListener);

    void update(@NonNull Consumer<YoDialogSpec.Builder> patch);

    /**
     * 在不重建内容树的前提下更新底部按钮启用态。
     */
    void setActionEnabled(@NonNull String actionId, boolean enabled);

    void dismiss(@NonNull DialogResult.Reason reason);
}
