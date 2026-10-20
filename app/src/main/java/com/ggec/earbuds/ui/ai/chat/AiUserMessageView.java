package com.ggec.earbuds.ui.ai.chat;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 用户发出的消息：靠右的浅色气泡，和系统消息应用里的灰色气泡一致。
 * 不用品牌色，长按弹出操作菜单。
 */
final class AiUserMessageView extends FrameLayout {

    private final TextView bubble;
    private AiMessageUi message;

    AiUserMessageView(@NonNull AiTheme theme, @NonNull AiMessageActions actions) {
        super(theme.context);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_S));

        bubble = theme.text(AiTheme.TEXT_BODY, theme.text1);
        bubble.setLineSpacing(theme.dpf(3), 1f);
        bubble.setPadding(theme.dp(14), theme.dp(10), theme.dp(14), theme.dp(10));
        bubble.setBackground(theme.corners(theme.brandSoft, 18, 18, 6, 18));
        bubble.setMaxWidth(Math.round(theme.context.getResources().getDisplayMetrics().widthPixels * 0.78f));
        bubble.setTextIsSelectable(false);
        addView(bubble, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END));

        bubble.setOnLongClickListener(view -> {
            if (message != null) {
                actions.onLongPress(bubble, message);
            }
            return true;
        });
    }

    void bind(@NonNull AiMessageUi next) {
        message = next;
        if (!next.text().contentEquals(bubble.getText())) {
            bubble.setText(next.text());
        }
    }
}
