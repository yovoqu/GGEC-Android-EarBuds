package com.yovo.yotheme.dialog.render;

import android.content.Context;
import android.graphics.Outline;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.api.style.DialogStyle;
import com.yovo.yotheme.dialog.performance.ShadowMode;
import com.yovo.yotheme.dialog.widget.DialogMetrics;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 为对话框面板内容应用 elevation 或软件绘制阴影
 */
public final class ShadowRenderer {

    private ShadowRenderer() {}

    @NonNull
    public static View wrap(@NonNull Context context,
                            @NonNull View contentView,
                            @NonNull DialogStyle style,
                            @NonNull ShadowMode shadowMode,
                            int horizontalScreenMarginPx) {
        switch (shadowMode) {
            case SOFTWARE_FALLBACK:
                return wrapWithSoftwareShadow(context, contentView, style, horizontalScreenMarginPx);
            case NONE:
                return wrapWithoutShadow(context, contentView, horizontalScreenMarginPx);
            case ELEVATION:
            default:
                return wrapWithElevation(context, contentView, style, horizontalScreenMarginPx);
        }
    }

    @NonNull
    private static View wrapWithoutShadow(@NonNull Context context,
                                          @NonNull View contentView,
                                          int horizontalScreenMarginPx) {
        FrameLayout host = new FrameLayout(context);
        host.setClipChildren(false);
        host.setClipToPadding(false);
        host.setPadding(horizontalScreenMarginPx, 0, horizontalScreenMarginPx, 0);
        host.addView(contentView, matchParentWrapContent());
        return host;
    }

    @NonNull
    private static View wrapWithElevation(@NonNull Context context,
                                          @NonNull View contentView,
                                          @NonNull DialogStyle style,
                                          int horizontalScreenMarginPx) {
        int shadowPaddingPx = DialogMetrics.dp(context, DialogConstants.SHADOW_PADDING.getValue());
        float cornerRadiusPx = DialogMetrics.dp(context, style.getCornerRadiusDp());
        float elevationPx = DialogMetrics.dp(context, DialogConstants.SHADOW_RADIUS.getValue());

        FrameLayout outerHost = new FrameLayout(context);
        outerHost.setClipChildren(false);
        outerHost.setClipToPadding(false);
        outerHost.setPadding(
                horizontalScreenMarginPx,
                shadowPaddingPx,
                horizontalScreenMarginPx,
                shadowPaddingPx);

        FrameLayout panel = new FrameLayout(context);
        panel.setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            panel.setElevation(elevationPx);
            panel.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });
        }
        panel.addView(contentView, matchParentWrapContent());
        outerHost.addView(panel, matchParentWrapContent());
        return outerHost;
    }

    @NonNull
    private static View wrapWithSoftwareShadow(@NonNull Context context,
                                               @NonNull View contentView,
                                               @NonNull DialogStyle style,
                                               int horizontalScreenMarginPx) {
        float cornerRadiusPx = DialogMetrics.dp(context, style.getCornerRadiusDp());
        int shadowPaddingPx = DialogMetrics.dp(context, DialogConstants.SHADOW_PADDING.getValue());
        DialogShadowContainer shadowHost = new DialogShadowContainer(
                context,
                cornerRadiusPx,
                DialogMetrics.dp(context, DialogConstants.SHADOW_RADIUS.getValue()),
                DialogMetrics.dp(context, DialogConstants.SHADOW_DY.getValue()),
                DialogConstants.SHADOW_ALPHA.getValue(),
                shadowPaddingPx,
                horizontalScreenMarginPx);
        shadowHost.addView(contentView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return shadowHost;
    }

    @NonNull
    private static FrameLayout.LayoutParams matchParentWrapContent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
