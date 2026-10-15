package com.yovo.yotheme.dialog.api.content;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 预构建的自定义 View 主体内容
 */
public final class CustomViewContent extends DialogContentSpec {

    @Nullable
    private final View view;

    @Nullable
    private final CustomContentBinder binder;

    public CustomViewContent(@NonNull View view) {
        this.view = view;
        this.binder = null;
    }

    public CustomViewContent(@NonNull CustomContentBinder binder) {
        this.view = null;
        this.binder = binder;
    }

    @Nullable
    public View getView() {
        return view;
    }

    @Nullable
    public CustomContentBinder getBinder() {
        return binder;
    }
}

