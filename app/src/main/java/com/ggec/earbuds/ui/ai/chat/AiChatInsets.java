package com.ggec.earbuds.ui.ai.chat;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.List;

/**
 * 对话页唯一的窗口留白消费者：页面根只吃状态栏高度，输入区让给悬浮标签栏和输入法。
 *
 * <p>去掉嵌套的 {@code YoPage} 之后顶部留白归页内，这里一并拿住；一个 View 只挂一个
 * listener，避免两层各补一遍。左右由 {@code YoTabs} 负责，这里不碰。</p>
 */
final class AiChatInsets {

    private static final float COMPOSER_BOTTOM_GAP_DP = 10f;

    private final AiTheme theme;
    private final View composer;

    private View root;
    private int statusBarTop;
    private int tabsInset;
    private int imeInset;

    AiChatInsets(@NonNull AiTheme theme, @NonNull View composer) {
        this.theme = theme;
        this.composer = composer;
    }

    void setTabsInset(int px) {
        if (tabsInset != px) {
            tabsInset = px;
            apply();
        }
    }

    void install(@NonNull View root) {
        this.root = root;
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            statusBarTop = YoSystemBar.systemBarsAndCutout(insets).top;
            imeInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            apply();
            return insets;
        });
        ViewCompat.setWindowInsetsAnimationCallback(root, new WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            @NonNull
            @Override
            public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets,
                                                 @NonNull List<WindowInsetsAnimationCompat> animations) {
                imeInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                apply();
                return insets;
            }
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void apply() {
        if (root != null) {
            root.setPadding(0, statusBarTop, 0, 0);
        }
        int bottom = Math.max(imeInset, tabsInset) + theme.dp(COMPOSER_BOTTOM_GAP_DP);
        if (composer.getPaddingBottom() != bottom) {
            composer.setPadding(composer.getPaddingLeft(), composer.getPaddingTop(),
                    composer.getPaddingRight(), bottom);
        }
    }
}
