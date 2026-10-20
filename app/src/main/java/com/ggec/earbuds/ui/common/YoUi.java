package com.ggec.earbuds.ui.common;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import com.yohu.youi.api.platform.YoSystemBar;

/**
 * 宿主窗口的统一入口：edge-to-edge、系统栏留白归属、dp 换算。
 *
 * <p>留白规则与 YoUI 的窗口级容器保持一致：
 * 顶部由页面自己负责（{@code YoPage} 标题栏，或 {@link #padTopForSystemBars(View)}）；
 * 左右和底部由 {@code YoTabs}/{@code YoPage} 负责，页面不得再补，否则会双份。</p>
 */
public final class YoUi {

    private YoUi() {
    }

    /** 透明系统栏 + 深浅色图标，所有 Activity 都在 onCreate 里调用一次。 */
    public static void edgeToEdge(@NonNull Activity activity) {
        YoSystemBar.prepareTransparentSystemBars(activity);
    }

    /**
     * 给普通页面补状态栏顶部留白。
     *
     * <p>只写 top，保留调用方已有的左右/底部 padding；不消费 inset，子视图仍能读到原始值。
     * 已经使用 {@code YoPage} 的页面不要调用，标题栏会自己吃状态栏。</p>
     */
    public static void padTopForSystemBars(@NonNull View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, insets) -> {
            int top = YoSystemBar.systemBarsAndCutout(insets).top;
            if (target.getPaddingTop() != top) {
                target.setPadding(target.getPaddingLeft(), top,
                        target.getPaddingRight(), target.getPaddingBottom());
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
