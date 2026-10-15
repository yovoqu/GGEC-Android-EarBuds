package com.yovo.yotheme.components.yo_loading.loading_structure;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.yovo.yotheme.system_set.SystemBarUtils;

/**
 * @author tingfeng.yang
 * @date 2026-06-15
 * @description Loading 顶层窗口配置与内容创建
 */
public final class LoadingWindowController {

    @NonNull
    public FrameLayout createContent(@NonNull Context context) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClickable(false);
        root.setFocusable(false);

        LoadingIndicatorView loadingView = new LoadingIndicatorView(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                loadingView.getPreferredSizePx(),
                loadingView.getPreferredSizePx());
        params.gravity = Gravity.CENTER;
        root.addView(loadingView, params);
        return root;
    }

    public void configure(@NonNull Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
        SystemBarUtils.applyFloatingStatusBar(window, dialog.getContext());
    }
}
