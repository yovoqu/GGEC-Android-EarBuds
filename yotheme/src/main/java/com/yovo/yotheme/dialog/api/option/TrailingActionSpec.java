package com.yovo.yotheme.dialog.api.option;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 选项行尾部操作的公共配置
 */
public final class TrailingActionSpec {

    private final int iconResId;
    @NonNull
    private final OnClickListener listener;

    public interface OnClickListener {
        void onClick(int index, @NonNull String itemId);
    }

    public TrailingActionSpec(int iconResId, @NonNull OnClickListener listener) {
        this.iconResId = iconResId;
        this.listener = listener;
    }

    public int getIconResId() {
        return iconResId;
    }

    @NonNull
    public OnClickListener getListener() {
        return listener;
    }
}

