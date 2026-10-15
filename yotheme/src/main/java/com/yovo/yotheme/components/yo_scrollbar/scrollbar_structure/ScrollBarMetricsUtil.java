package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import android.content.Context;
import android.util.TypedValue;

/** 滚动条尺寸换算。 */
public final class ScrollBarMetricsUtil {

    private ScrollBarMetricsUtil() {}

    public static int dpToPx(Context context, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()));
    }

    public static float dpToPxF(Context context, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics());
    }
}
