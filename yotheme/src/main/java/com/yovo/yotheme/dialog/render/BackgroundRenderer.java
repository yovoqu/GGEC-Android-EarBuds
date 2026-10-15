package com.yovo.yotheme.dialog.render;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.yovo.yotheme.background.shapeblurbiew.YoBlurBackgroundView;
import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.performance.BlurMode;
import com.yovo.yotheme.dialog.performance.DialogPerformancePolicy;
import com.yovo.yotheme.dialog.widget.DialogMetrics;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 为对话框内容包裹纯色或模糊面板背景
 */
public final class BackgroundRenderer {

    public BackgroundRenderer() {}

    @NonNull
    public View renderPanel(@NonNull Context context,
                            @NonNull View contentLayout,
                            @NonNull YoDialogSpec spec) {
        DialogPerformancePolicy policy = spec.getPerformancePolicy();
        View withBackground = wrap(context, contentLayout, spec, policy);
        return DialogFrameRenderer.wrap(context, withBackground, spec, policy);
    }

    @NonNull
    public static View wrap(@NonNull Context context,
                            @NonNull View contentLayout,
                            @NonNull YoDialogSpec spec,
                            @NonNull DialogPerformancePolicy policy) {
        BlurMode blurMode = policy.getBlurMode();
        if (blurMode == BlurMode.STATIC_SNAPSHOT || blurMode == BlurMode.REALTIME_OPT_IN) {
            return wrapWithBlur(context, contentLayout, spec);
        }
        return wrapWithSolidBackground(context, contentLayout, spec);
    }

    @NonNull
    private static View wrapWithSolidBackground(@NonNull Context context,
                                                @NonNull View contentLayout,
                                                @NonNull YoDialogSpec spec) {
        FrameLayout container = new FrameLayout(context);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        container.setBackground(createRoundedBackground(context, spec));
        container.addView(contentLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return container;
    }

    @NonNull
    private static View wrapWithBlur(@NonNull Context context,
                                     @NonNull View contentLayout,
                                     @NonNull YoDialogSpec spec) {
        int baseColor = ColorMapper.map(YoThemeColor.BACKGROUND_PRIMARY);
        FrameLayout blurContainer = YoBlurBackgroundView.createForDialog(context, contentLayout, baseColor);
        blurContainer.setClipChildren(false);
        blurContainer.setClipToPadding(false);

        GradientDrawable shadowOutline = new GradientDrawable();
        shadowOutline.setColor(Color.TRANSPARENT);
        shadowOutline.setCornerRadius(DialogMetrics.dp(context, spec.getStyle().getCornerRadiusDp()));
        blurContainer.setBackground(shadowOutline);
        return blurContainer;
    }

    @NonNull
    static GradientDrawable createRoundedBackground(@NonNull Context context, @NonNull YoDialogSpec spec) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorMapper.map(YoThemeColor.BACKGROUND_PRIMARY));
        background.setCornerRadius(DialogMetrics.dp(context, spec.getStyle().getCornerRadiusDp()));
        return background;
    }
}
