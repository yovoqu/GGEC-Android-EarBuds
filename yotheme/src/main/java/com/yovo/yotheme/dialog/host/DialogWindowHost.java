package com.yovo.yotheme.dialog.host;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.yovo.yotheme.system_set.SystemBarUtils;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description YoDialog 的平台窗口配置与遮罩根布局组装
 */
public final class DialogWindowHost {

    @NonNull
    public DialogMaskHost createMaskedRoot(@NonNull Context context,
                                           float maskAlpha,
                                           @NonNull View dialogContent,
                                           @NonNull Runnable onOutsideDismiss) {
        DialogMaskHost root = new DialogMaskHost(context, maskAlpha, onOutsideDismiss);
        root.mountDialogContent(dialogContent);
        root.prepareForShow();
        return root;
    }

    public void configure(@NonNull Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;
        params.x = 0;
        params.y = 0;
        window.setAttributes(params);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        SystemBarUtils.applyFloatingDialogWindow(window, dialog.getContext());
    }

    public void bindContentInsets(@NonNull Dialog dialog, @NonNull DialogMaskHost maskHost) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        View decorView = window.getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            maskHost.applyContentInsets(systemBars);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(decorView);
    }
}
