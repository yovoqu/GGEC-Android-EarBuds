package com.yovo.yotheme.dialog.api.content;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 创建并绑定自定义对话框主体内容
 */
public interface CustomContentBinder {

    @NonNull
    View createView(@NonNull Context context);

    default void bind(@NonNull View contentView) {
        // 可选的创建后绑定钩子。
    }

    default void unbind(@NonNull View contentView) {
        // 可选的清理钩子。
    }
}

