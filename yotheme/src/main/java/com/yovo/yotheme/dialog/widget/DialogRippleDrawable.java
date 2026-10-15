package com.yovo.yotheme.dialog.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.annotation.NonNull;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.DialogConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 对话框按压反馈 Drawable 工厂
 * @note Dialog 阴影容器使用 LAYER_TYPE_SOFTWARE 绘制，RippleDrawable 无法在该 Canvas 上动画，因此统一使用 StateListDrawable 实现按压态
 */
public final class DialogRippleDrawable {

    private DialogRippleDrawable() {}

    public static Drawable button(Context context) {
        return createPressedStateList(
                context,
                DialogConstants.CORNER_RADIUS.getValue(),
                ColorMapper.map(YoThemeColor.TRANSPARENT));
    }

    /** 列表项背景：默认无 ripple，圆角为 Dialog 圆角的一半。 */
    public static Drawable listItem(@NonNull Context context,
                                    float dialogCornerRadiusDp,
                                    boolean rippleEnabled) {
        if (!rippleEnabled) {
            return new ColorDrawable(Color.TRANSPARENT);
        }
        float rippleCornerRadiusDp = dialogCornerRadiusDp / 2f;
        return createPressedStateList(
                context,
                rippleCornerRadiusDp,
                Color.TRANSPARENT);
    }

    private static Drawable createPressedStateList(Context context, float cornerRadiusDp, int normalColor) {
        float cornerRadiusPx = DialogMetrics.dp(context, cornerRadiusDp);
        int pressedColor = ColorMapper.map(YoThemeColor.INTERACTIVE_PRESSED);

        GradientDrawable pressedDrawable = new GradientDrawable();
        pressedDrawable.setColor(pressedColor);
        pressedDrawable.setCornerRadius(cornerRadiusPx);

        GradientDrawable normalDrawable = new GradientDrawable();
        normalDrawable.setColor(normalColor);
        normalDrawable.setCornerRadius(cornerRadiusPx);

        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        stateListDrawable.addState(new int[]{}, normalDrawable);
        return stateListDrawable;
    }
}
