package com.ggec.earbuds.ui.ai.chat;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.widget.AiTypingDotsView;
import com.ggec.earbuds.ui.ai.widget.MarkdownView;

/**
 * 助手的一条回复：头部（头像和模型名）、可折叠的思考过程、正文（Markdown）、
 * 异常说明（停止、截断、出错）、底部的复制重试删除和耗时。
 * 流式输出时同一个视图会被反复 {@link #bind}，只有真正变化的部分才会重画。
 */
final class AiAssistantMessageView extends LinearLayout {

    private final AiTheme theme;
    private final AiMessageActions actions;

    private final TextView name;
    private final AiReasoningView reasoning;
    private final AiTypingDotsView typing;
    private final MarkdownView markdown;
    private final AiMessageNoticeView notice;
    private final AiAssistantFooterView footer;

    private AiMessageUi message;
    private String shownText = "";

    AiAssistantMessageView(@NonNull AiTheme theme, @NonNull AiMessageActions actions) {
        super(theme.context);
        this.theme = theme;
        this.actions = actions;
        setOrientation(VERTICAL);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_M),
                theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_S));

        // 头部：只留一行模型名，字号和系统列表里的说明文字一样，不放头像和图标。
        LinearLayout header = new LinearLayout(theme.context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        name = theme.text(AiTheme.TEXT_CAPTION, theme.text3, AiTheme.Weight.MEDIUM);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        reasoning = new AiReasoningView(theme, actions);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = theme.dp(8);
        addView(reasoning, cardParams);

        // 等待首字
        typing = new AiTypingDotsView(theme.context, theme.text3, theme.density);
        LinearLayout.LayoutParams typingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        typingParams.topMargin = theme.dp(6);
        addView(typing, typingParams);

        // 正文
        markdown = new MarkdownView(theme, AiTheme.TEXT_BODY, theme.text1, new MarkdownView.Callbacks() {
            @Override
            public void onLinkClick(@NonNull String url) {
                actions.onLink(url);
            }

            @Override
            public void onCopyCode(@NonNull String code) {
                actions.onCopyCode(code);
            }
        });
        LinearLayout.LayoutParams markdownParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        markdownParams.topMargin = theme.dp(8);
        addView(markdown, markdownParams);

        notice = new AiMessageNoticeView(theme, new AiMessageNoticeView.Listener() {
            @Override
            public void onContinue() {
                actions.onContinue();
            }

            @Override
            public void onRetry() {
                actions.onRegenerate();
            }
        });
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noticeParams.topMargin = theme.dp(10);
        addView(notice, noticeParams);

        footer = new AiAssistantFooterView(theme, new AiAssistantFooterView.Listener() {
            @Override
            public void onCopy(@NonNull String text) {
                actions.onCopy(text);
            }

            @Override
            public void onRegenerate() {
                actions.onRegenerate();
            }

            @Override
            public void onDelete(@NonNull String messageId) {
                actions.onDelete(messageId);
            }
        });
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(40));
        footerParams.topMargin = theme.dp(4);
        footerParams.setMarginStart(-theme.dp(10));
        addView(footer, footerParams);

        setOnLongClickListener(view -> {
            if (message != null && !message.streaming()) {
                actions.onLongPress(this, message);
            }
            return true;
        });
    }

    void bind(@NonNull AiMessageUi next) {
        message = next;
        name.setText(next.modelLabel().isEmpty() ? theme.context.getString(R.string.ai_assistant_name)
                : next.modelLabel());
        reasoning.bind(next);
        typing.setVisibility(next.waitingForFirstToken() ? VISIBLE : GONE);
        boolean hasText = !next.text().isEmpty();
        markdown.setVisibility(hasText ? VISIBLE : GONE);
        if (hasText && !next.text().equals(shownText)) {
            markdown.setMarkdown(next.text());
        }
        shownText = next.text();
        notice.bind(next);
        footer.bind(next);
    }
}
