package com.ggec.earbuds.ui.shell;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.common.YoUi;
import com.yohu.youi.api.theme.YoColors;
import com.yohu.youi.api.theme.YoDimens;
import com.yohu.youi.api.theme.YoFonts;

/**
 * 设备 / 我的页签的占位页：背景铺满整屏，标题居中。
 *
 * <p>左右与底部留白归 {@code YoTabs}；顶部状态栏由这里调用
 * {@link YoUi#padTopForSystemBars} 补，避免标题被状态栏盖住，背景仍然铺到全屏。</p>
 */
public final class ShellPageView {

    private ShellPageView() {
    }

    @NonNull
    public static FrameLayout create(@NonNull Context context, @NonNull CharSequence title) {
        YoColors colors = YoColors.of(context);
        FrameLayout page = new FrameLayout(context);
        page.setBackgroundColor(colors.palette().snowGray(1));

        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(YoDimens.TEXT_SIZE_HEADLINE);
        label.setTextColor(colors.textPrimary());
        label.setGravity(Gravity.CENTER);
        YoFonts.applyBold(label);
        page.addView(label, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        YoUi.padTopForSystemBars(page);
        return page;
    }
}
