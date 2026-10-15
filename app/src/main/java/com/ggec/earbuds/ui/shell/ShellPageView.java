package com.ggec.earbuds.ui.shell;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.yohu.youi.api.theme.YoDimens;
import com.yohu.youi.api.theme.YoFonts;

public final class ShellPageView {

    private ShellPageView() {
    }

    @NonNull
    public static FrameLayout create(@NonNull Context context,
                                     @NonNull CharSequence title,
                                     @ColorInt int backgroundColor,
                                     @ColorInt int titleColor) {
        FrameLayout page = new FrameLayout(context);
        page.setBackgroundColor(backgroundColor);
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(YoDimens.TEXT_SIZE_HEADLINE);
        label.setTextColor(titleColor);
        label.setGravity(Gravity.CENTER);
        YoFonts.applyBold(label);
        page.addView(label, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return page;
    }
}
