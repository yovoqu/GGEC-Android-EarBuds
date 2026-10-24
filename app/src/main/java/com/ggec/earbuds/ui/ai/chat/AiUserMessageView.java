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

    /** 超过这个行数折叠：一屏都是自己说的话，就看不见回答了。 */
    private static final int MAX_COLLAPSED_LINES = 6;

    private final TextView bubble;
    private final TextView expand;
    private AiMessageUi message;
    private boolean expanded;

    AiUserMessageView(@NonNull AiTheme theme, @NonNull AiMessageActions actions) {
        super(theme.context);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_S));

        bubble = theme.text(AiTheme.TEXT_BODY, theme.text1);
        bubble.setLineSpacing(theme.dpf(3), 1f);
        bubble.setPadding(theme.dp(15), theme.dp(10), theme.dp(15), theme.dp(10));
        // 中性灰，不用品牌色：对话两侧都该是"内容"，品牌色只留给主操作与进行中的状态
        // （ChatGPT / Claude / Gemini 三家在这一点上一致）。之前用品牌淡蓝，
        // 会和"正在生成"的蓝色抢同一个信号。
        bubble.setBackground(theme.corners(theme.fill, 18, 18, 6, 18));
        bubble.setMaxWidth(Math.round(theme.context.getResources().getDisplayMetrics().widthPixels * 0.82f));
        bubble.setTextIsSelectable(false);
        addView(bubble, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END));

        expand = theme.text(AiTheme.TEXT_SUBHEAD, theme.brand);
        expand.setText(com.ggec.earbuds.R.string.ai_message_expand);
        expand.setPadding(0, theme.dp(AiTheme.SPACE_XS), theme.dp(AiTheme.SPACE_S), 0);
        expand.setVisibility(GONE);
        expand.setOnClickListener(view -> {
            expanded = !expanded;
            applyFolding();
        });
        FrameLayout.LayoutParams expandParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END);
        expandParams.topMargin = theme.dp(AiTheme.SPACE_XS);
        addView(expand, expandParams);

        bubble.setOnLongClickListener(view -> {
            if (message != null) {
                actions.onLongPress(bubble, message);
            }
            return true;
        });
    }

    void bind(@NonNull AiMessageUi next) {
        boolean sameText = next.text().contentEquals(bubble.getText());
        message = next;
        if (!sameText) {
            bubble.setText(next.text());
            expanded = false;
        }
        applyFolding();
    }

    /**
     * 折叠只在真的超过 6 行时出现。用 post 是因为行数要等布局完成才知道，
     * 之前按字符数估算，中英混排估错得很离谱。
     */
    private void applyFolding() {
        bubble.setMaxLines(MAX_COLLAPSED_LINES);
        bubble.post(() -> {
            if (bubble.getLineCount() <= MAX_COLLAPSED_LINES) {
                expand.setVisibility(GONE);
                return;
            }
            bubble.setMaxLines(Integer.MAX_VALUE);
            int fullHeight = bubble.getHeight();
            bubble.setMaxLines(MAX_COLLAPSED_LINES);
            boolean overflows = fullHeight > bubble.getHeight();
            expand.setVisibility(overflows || expanded ? VISIBLE : GONE);
            bubble.setMaxLines(expanded ? Integer.MAX_VALUE : MAX_COLLAPSED_LINES);
            expand.setText(expanded
                    ? com.ggec.earbuds.R.string.ai_message_collapse
                    : com.ggec.earbuds.R.string.ai_message_expand);
        });
    }
}
