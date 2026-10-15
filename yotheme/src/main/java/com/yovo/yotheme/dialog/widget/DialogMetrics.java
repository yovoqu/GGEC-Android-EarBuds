package com.yovo.yotheme.dialog.widget;

import android.content.Context;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 对话框尺寸换算工具
 */
public final class DialogMetrics {

    private DialogMetrics() {}

    public static int dp(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /** 1px 发丝线高度，用于列表分割线。 */
    public static int hairline(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(1f / density));
    }
}
