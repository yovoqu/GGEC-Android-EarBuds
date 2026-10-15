package com.yovo.yotheme.dialog.api.option;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 标题栏左侧扩展（如图标操作入口）
 */
public final class HeaderLeadingSpec {

    public interface OnClickListener {
        void onClick(@NonNull List<String> selectedIds);
    }

    @DrawableRes
    private final int iconResId;

    @Nullable
    private final OnClickListener listener;

    private final boolean enableWhenHasSelection;

    private HeaderLeadingSpec(@DrawableRes int iconResId,
                              @Nullable OnClickListener listener,
                              boolean enableWhenHasSelection) {
        this.iconResId = iconResId;
        this.listener = listener;
        this.enableWhenHasSelection = enableWhenHasSelection;
    }

    /**
     * 左侧图标入口；默认仅在有选中项时可点击。
     */
    @NonNull
    public static HeaderLeadingSpec icon(@DrawableRes int iconResId,
                                         @NonNull OnClickListener listener) {
        return new HeaderLeadingSpec(iconResId, listener, true);
    }

    @DrawableRes
    public int getIconResId() {
        return iconResId;
    }

    @Nullable
    public OnClickListener getListener() {
        return listener;
    }

    public boolean isEnableWhenHasSelection() {
        return enableWhenHasSelection;
    }
}
