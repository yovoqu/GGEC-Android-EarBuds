package com.yovo.yotheme.components.yo_loading;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;

import com.yovo.yotheme.components.yo_loading.loading_structure.LoadingStateManager;
import com.yovo.yotheme.components.yo_loading.loading_structure.LoadingWindowController;

/**
 * @author tingfeng.yang
 * @date 2026-06-15
 * @description YoTheme Loading 组件入口
 * @note 窗口、状态、动画与绘制均委托给 loading_structure，避免入口类承担底层细节
 */
public final class YoLoading extends Dialog implements YoLoadingInterface {

    private final LoadingStateManager stateManager = new LoadingStateManager();
    private final LoadingWindowController windowController = new LoadingWindowController();
    private boolean dismissingFromState;

    public static YoLoading create(@NonNull Context context) {
        return new YoLoading(context);
    }

    private YoLoading(@NonNull Context context) {
        super(context, com.yovo.yotheme.R.style.YoTheme_FloatingWindow);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        windowController.configure(this);
        stateManager.setCallback(visible -> {
            if (visible) {
                showLoadingWindow();
            } else {
                dismissLoadingWindow();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(windowController.createContent(getContext()));
        windowController.configure(this);
    }

    @Override
    public void render(boolean visible) {
        stateManager.setVisible(visible);
    }

    @Override
    public void showLoading() {
        stateManager.setVisible(true);
    }

    @Override
    public void hideLoading() {
        stateManager.setVisible(false);
    }

    @Override
    public boolean isLoadingVisible() {
        return stateManager.isVisible();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (!dismissingFromState) {
            stateManager.setVisible(false);
        }
    }

    private void showLoadingWindow() {
        if (!isShowing()) {
            windowController.configure(this);
            show();
        }
        windowController.configure(this);
    }

    private void dismissLoadingWindow() {
        if (isShowing()) {
            dismissingFromState = true;
            dismiss();
            dismissingFromState = false;
        }
    }
}
