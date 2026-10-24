package com.ggec.earbuds.ui.ai.chat;

import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.common.AiFormat;
import com.ggec.earbuds.ui.ai.common.AiMessages;
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
    private final LinearLayout reasoningCard;
    private final TextView reasoningTitle;
    private final ImageView reasoningChevron;
    private final TextView reasoningBody;
    private final AiTypingDotsView typing;
    private final MarkdownView markdown;
    private final LinearLayout notice;
    private final ImageView noticeIcon;
    private final TextView noticeText;
    private final TextView noticeDetail;
    private final TextView noticeAction;
    private final LinearLayout footer;
    private final ImageView copyButton;
    private final ImageView regenerateButton;
    private final ImageView deleteButton;
    private final TextView meta;

    private AiMessageUi message;
    private String shownText = "";
    private String shownReasoning = "";

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

        // 思考过程
        reasoningCard = new LinearLayout(theme.context);
        reasoningCard.setOrientation(VERTICAL);
        reasoningCard.setBackground(theme.rounded(theme.fill, 12));
        reasoningCard.setPadding(theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_S));
        LinearLayout reasoningHeader = new LinearLayout(theme.context);
        reasoningHeader.setOrientation(HORIZONTAL);
        reasoningHeader.setGravity(Gravity.CENTER_VERTICAL);
        reasoningHeader.setOnClickListener(view -> {
            if (message != null) {
                actions.onToggleReasoning(message.id());
            }
        });
        reasoningTitle = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2, AiTheme.Weight.MEDIUM);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        reasoningHeader.addView(reasoningTitle, titleParams);
        reasoningChevron = theme.icon(R.drawable.ic_ai_chevron_down, theme.text3);
        reasoningHeader.addView(reasoningChevron, new LinearLayout.LayoutParams(
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_L)));
        reasoningCard.addView(reasoningHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        reasoningBody = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        reasoningBody.setLineSpacing(theme.dpf(3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = theme.dp(8);
        reasoningCard.addView(reasoningBody, bodyParams);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = theme.dp(8);
        addView(reasoningCard, cardParams);

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

        // 异常说明
        notice = new LinearLayout(theme.context);
        notice.setOrientation(VERTICAL);
        notice.setPadding(theme.dp(12), theme.dp(10), theme.dp(12), theme.dp(10));
        LinearLayout noticeRow = new LinearLayout(theme.context);
        noticeRow.setOrientation(HORIZONTAL);
        noticeIcon = theme.icon(R.drawable.ic_ai_warning, theme.warning);
        LinearLayout.LayoutParams noticeIconParams = new LinearLayout.LayoutParams(theme.dp(16), theme.dp(16));
        noticeIconParams.topMargin = theme.dp(1);
        noticeRow.addView(noticeIcon, noticeIconParams);
        LinearLayout noticeTexts = new LinearLayout(theme.context);
        noticeTexts.setOrientation(VERTICAL);
        noticeText = theme.text(13.5f, theme.text1);
        noticeText.setLineSpacing(theme.dpf(2), 1f);
        noticeTexts.addView(noticeText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        noticeDetail = theme.text(12, theme.text3);
        noticeDetail.setMaxLines(4);
        noticeDetail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = theme.dp(4);
        noticeTexts.addView(noticeDetail, detailParams);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.setMarginStart(theme.dp(8));
        noticeRow.addView(noticeTexts, textsParams);
        notice.addView(noticeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        noticeAction = theme.text(13, theme.brand, AiTheme.Weight.MEDIUM);
        noticeAction.setPadding(theme.dp(12), theme.dp(6), theme.dp(12), theme.dp(6));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = theme.dp(8);
        notice.addView(noticeAction, actionParams);
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noticeParams.topMargin = theme.dp(10);
        addView(notice, noticeParams);

        // 底部操作
        footer = new LinearLayout(theme.context);
        footer.setOrientation(HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        copyButton = footerButton(R.drawable.ic_ai_copy, R.string.ai_copy);
        regenerateButton = footerButton(R.drawable.ic_ai_refresh, R.string.ai_action_regenerate);
        deleteButton = footerButton(R.drawable.ic_ai_delete, R.string.ai_delete);
        footer.addView(copyButton);
        footer.addView(regenerateButton);
        // 删除不可恢复，和复制之间留一段空，别让两个图标读成一组。
        View deleteGap = new View(theme.context);
        footer.addView(deleteGap, new LinearLayout.LayoutParams(theme.dp(12), 1));
        footer.addView(deleteButton);
        meta = theme.text(12, theme.text3);
        meta.setGravity(Gravity.END);
        meta.setSingleLine(true);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metaParams.setMarginStart(theme.dp(8));
        footer.addView(meta, metaParams);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(40));
        footerParams.topMargin = theme.dp(4);
        footerParams.setMarginStart(-theme.dp(10));
        addView(footer, footerParams);

        copyButton.setOnClickListener(view -> {
            if (message != null) {
                actions.onCopy(message.text());
            }
        });
        regenerateButton.setOnClickListener(view -> actions.onRegenerate());
        deleteButton.setOnClickListener(view -> {
            if (message != null) {
                actions.onDelete(message.id());
            }
        });
        noticeAction.setOnClickListener(view -> {
            if (message != null && message.canContinue()) {
                actions.onContinue();
            } else {
                actions.onRegenerate();
            }
        });
        setOnLongClickListener(view -> {
            if (message != null && !message.streaming()) {
                actions.onLongPress(this, message);
            }
            return true;
        });
    }

    private ImageView footerButton(@DrawableRes int icon, @StringRes int description) {
        return theme.iconButton(icon, theme.text3, 40, 10, theme.context.getString(description));
    }

    void bind(@NonNull AiMessageUi next) {
        message = next;
        name.setText(next.modelLabel().isEmpty() ? theme.context.getString(R.string.ai_assistant_name)
                : next.modelLabel());
        bindReasoning(next);
        typing.setVisibility(next.waitingForFirstToken() ? VISIBLE : GONE);
        boolean hasText = !next.text().isEmpty();
        markdown.setVisibility(hasText ? VISIBLE : GONE);
        if (hasText && !next.text().equals(shownText)) {
            markdown.setMarkdown(next.text());
        }
        shownText = next.text();
        bindNotice(next);
        bindFooter(next);
    }

    private void bindReasoning(AiMessageUi next) {
        String reasoning = next.reasoning();
        reasoningCard.setVisibility(reasoning == null ? GONE : VISIBLE);
        if (reasoning == null) {
            shownReasoning = "";
            return;
        }
        boolean thinking = next.streaming() && next.text().isEmpty();
        boolean expanded = next.reasoningExpanded();
        reasoningTitle.setText(thinking ? R.string.ai_reasoning_thinking : R.string.ai_reasoning_title);
        reasoningBody.setVisibility(expanded ? VISIBLE : GONE);
        reasoningChevron.setRotation(expanded ? 180f : 0f);
        // 收起时只是一行"有什么可展开"，不该占一块最重的灰底；
        // 展开才需要容器来界定那一大段低优先级文字（Claude / Gemini 同此形态）。
        reasoningCard.setBackground(expanded
                ? theme.rounded(theme.fill, AiTheme.RADIUS_ROW) : null);
        int insetX = expanded ? theme.dp(AiTheme.SPACE_M) : 0;
        int insetY = expanded ? theme.dp(AiTheme.SPACE_S) : theme.dp(AiTheme.SPACE_XS);
        if (reasoningCard.getPaddingLeft() != insetX || reasoningCard.getPaddingTop() != insetY) {
            reasoningCard.setPadding(insetX, insetY, insetX, insetY);
        }
        if (expanded && !reasoning.equals(shownReasoning)) {
            reasoningBody.setText(reasoning.strip());
            shownReasoning = reasoning;
        }
    }

    private void bindNotice(AiMessageUi next) {
        if (next.notice() == AiMessageUi.Notice.NONE) {
            notice.setVisibility(GONE);
            return;
        }
        notice.setVisibility(VISIBLE);
        @ColorInt int soft;
        @ColorInt int accent;
        @DrawableRes int icon;
        String text;
        String detail = "";
        boolean offerContinue = false;
        boolean offerRetry = false;
        switch (next.notice()) {
            case STOPPED -> {
                soft = theme.fill;
                accent = theme.text3;
                icon = R.drawable.ic_ai_stop;
                text = theme.context.getString(R.string.ai_problem_stopped);
            }
            case TRUNCATED -> {
                soft = theme.warningSoft;
                accent = theme.warning;
                icon = R.drawable.ic_ai_warning;
                text = theme.context.getString(R.string.ai_problem_truncated);
                offerContinue = next.canContinue();
            }
            case FILTERED -> {
                soft = theme.warningSoft;
                accent = theme.warning;
                icon = R.drawable.ic_ai_shield;
                text = theme.context.getString(R.string.ai_problem_filtered);
            }
            case EMPTY -> {
                soft = theme.fill;
                accent = theme.text3;
                icon = R.drawable.ic_ai_warning;
                text = theme.context.getString(R.string.ai_problem_empty);
                offerRetry = next.canRegenerate();
            }
            default -> {
                soft = theme.alertSoft;
                accent = theme.alert;
                icon = R.drawable.ic_ai_warning;
                text = next.failureKind() == null
                        ? theme.context.getString(R.string.ai_failure_unknown)
                        : theme.context.getString(AiMessages.failure(next.failureKind()));
                detail = next.failureDetail();
                offerRetry = next.canRegenerate();
            }
        }
        notice.setBackground(theme.rounded(soft, 12));
        noticeIcon.setImageResource(icon);
        noticeIcon.setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        noticeText.setText(text);
        noticeDetail.setText(detail);
        noticeDetail.setVisibility(detail.isBlank() ? GONE : VISIBLE);
        boolean showAction = offerContinue || offerRetry;
        noticeAction.setVisibility(showAction ? VISIBLE : GONE);
        if (showAction) {
            noticeAction.setText(offerContinue ? R.string.ai_continue : R.string.ai_retry);
            noticeAction.setTextColor(accent == theme.text3 ? theme.brand : accent);
            noticeAction.setBackground(theme.pressable(
                    theme.rounded(theme.card, 14), 14));
        }
    }

    private void bindFooter(AiMessageUi next) {
        boolean hasText = !next.text().isEmpty();
        boolean showCopy = hasText;
        boolean showRegenerate = next.canRegenerate() && next.notice() != AiMessageUi.Notice.ERROR
                && next.notice() != AiMessageUi.Notice.EMPTY;
        boolean showDelete = next.canDelete();
        boolean any = !next.streaming() && (showCopy || showRegenerate || showDelete);
        footer.setVisibility(any ? VISIBLE : GONE);
        copyButton.setVisibility(showCopy ? VISIBLE : GONE);
        regenerateButton.setVisibility(showRegenerate ? VISIBLE : GONE);
        deleteButton.setVisibility(showDelete ? VISIBLE : GONE);
        StringBuilder text = new StringBuilder();
        if (next.elapsedMillis() > 0) {
            text.append(AiFormat.duration(next.elapsedMillis()));
        }
        // 速率紧跟耗时：这两个数一起才说明"等多久、之后多快"。
        int tps = com.ggec.earbuds.ui.ai.common.AiFormat.tokensPerSecond(
                next.outputTokens(), next.elapsedMillis());
        if (tps > 0) {
            if (text.length() > 0) {
                text.append(theme.context.getString(R.string.ai_footer_separator));
            }
            text.append(theme.context.getString(R.string.ai_value_speed, tps));
        }
        if (next.outputTokens() > 0) {
            if (text.length() > 0) {
                text.append(theme.context.getString(R.string.ai_footer_separator));
            }
            text.append(theme.context.getString(R.string.ai_value_tokens, next.outputTokens()));
        }
        meta.setText(text);
        meta.setVisibility(text.length() == 0 ? INVISIBLE : VISIBLE);
    }
}
