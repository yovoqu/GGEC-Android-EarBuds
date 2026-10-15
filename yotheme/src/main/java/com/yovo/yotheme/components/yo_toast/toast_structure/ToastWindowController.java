package com.yovo.yotheme.components.yo_toast.toast_structure;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.yovo.yotheme.R;
import com.yovo.yotheme.constants.ToastConstants;
import com.yovo.yotheme.system_set.SystemBarUtils;

/**
 * @author tingfeng.yang
 * @date 2026-06-24
 * @description YoToast 顶层窗口配置与内容创建
 * @note 窗口铺满屏幕但不可聚焦、不可触摸，内容居中，触摸事件穿透到底层界面
 */
public final class ToastWindowController {

    @NonNull
    public View createContent(@NonNull Context context, @NonNull CharSequence message) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClickable(false);
        root.setFocusable(false);

        TextView textView = createTextView(context, message);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        int marginH = dpToPx(context, ToastConstants.MARGIN_HORIZONTAL_DP.getFloatValue());
        params.leftMargin = marginH;
        params.rightMargin = marginH;
        root.addView(textView, params);
        return root;
    }

    @NonNull
    private TextView createTextView(@NonNull Context context, @NonNull CharSequence message) {
        TextView textView = new TextView(context);
        textView.setText(message);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                ToastConstants.TEXT_SIZE_SP.getFloatValue());
        textView.setGravity(Gravity.CENTER);
        textView.setLineSpacing(0f, ToastConstants.LINE_SPACING_MULTIPLIER.getFloatValue());

        int padH = dpToPx(context, ToastConstants.PADDING_HORIZONTAL_DP.getFloatValue());
        int padV = dpToPx(context, ToastConstants.PADDING_VERTICAL_DP.getFloatValue());
        textView.setPadding(padH, padV, padH, padV);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        textView.setMaxWidth(Math.round(
                metrics.widthPixels * ToastConstants.MAX_WIDTH_FACTOR.getFloatValue()));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(ToastConstants.BACKGROUND_ALPHA.getIntValue(), 0, 0, 0));
        background.setCornerRadius(dpToPx(context, ToastConstants.CORNER_RADIUS_DP.getFloatValue()));
        textView.setBackground(background);
        return textView;
    }

    public void configure(@NonNull Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
        window.setWindowAnimations(R.style.YoTheme_Toast_Animation);
        SystemBarUtils.applyFloatingStatusBar(window, dialog.getContext());
    }

    private static int dpToPx(@NonNull Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
