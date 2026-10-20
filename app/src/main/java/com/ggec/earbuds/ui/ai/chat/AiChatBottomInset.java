package com.ggec.earbuds.ui.ai.chat;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ggec.earbuds.ui.ai.AiTheme;

import java.util.List;

/**
 * 对话页底部留白：悬浮标签栏和输入法谁高让谁。
 *
 * <p>标签栏高度来自 {@code YoTabs.getContentInsetPx()}；输入法高度来自窗口 inset 与动画进度。
 * 只负责给底部输入栏垫 padding，不碰其它视图。</p>
 */
final class AiChatBottomInset {

    private final AiTheme theme;
    private final View target;

    private int tabsInset;
    private int imeInset;

    AiChatBottomInset(@NonNull AiTheme theme, @NonNull View target) {
        this.theme = theme;
        this.target = target;
    }

    void setTabsInset(int px) {
        if (tabsInset != px) {
            tabsInset = px;
            apply();
        }
    }

    /** 在页面内容根上订阅 IME inset 与动画进度。 */
    void install(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
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
    }

    private void apply() {
        int bottom = Math.max(imeInset, tabsInset);
        target.setPadding(theme.dp(8), 0, theme.dp(8), bottom + theme.dp(4));
    }
}
