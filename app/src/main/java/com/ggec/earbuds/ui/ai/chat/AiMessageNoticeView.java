package com.ggec.earbuds.ui.ai.chat;

import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.common.AiMessages;

/** 回复下方的问题说明：停止、截断、过滤、空回复或错误，以及唯一可执行的恢复动作。 */
final class AiMessageNoticeView extends LinearLayout {

    interface Listener {
        void onContinue();
        void onRetry();
    }

    private final AiTheme theme;
    private final Listener listener;
    private final ImageView icon;
    private final TextView text;
    private final TextView detail;
    private final TextView action;
    private boolean continueAction;

    AiMessageNoticeView(@NonNull AiTheme theme, @NonNull Listener listener) {
        super(theme.context);
        this.theme = theme;
        this.listener = listener;
        setOrientation(VERTICAL);
        setPadding(theme.dp(12), theme.dp(10), theme.dp(12), theme.dp(10));

        LinearLayout row = new LinearLayout(theme.context);
        row.setOrientation(HORIZONTAL);
        icon = theme.icon(R.drawable.ic_ai_warning, theme.warning);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                theme.dp(16), theme.dp(16));
        iconParams.topMargin = theme.dp(1);
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(theme.context);
        texts.setOrientation(VERTICAL);
        text = theme.text(13.5f, theme.text1);
        text.setLineSpacing(theme.dpf(2), 1f);
        texts.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        detail = theme.text(12, theme.text3);
        detail.setMaxLines(4);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = theme.dp(4);
        texts.addView(detail, detailParams);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.setMarginStart(theme.dp(8));
        row.addView(texts, textsParams);
        addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        action = theme.text(13, theme.brand, AiTheme.Weight.MEDIUM);
        action.setPadding(theme.dp(12), theme.dp(6), theme.dp(12), theme.dp(6));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = theme.dp(8);
        addView(action, actionParams);
        action.setOnClickListener(view -> {
            if (continueAction) {
                listener.onContinue();
            } else {
                listener.onRetry();
            }
        });
    }

    void bind(@NonNull AiMessageUi message) {
        if (message.notice() == AiMessageUi.Notice.NONE) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        NoticeStyle style = styleFor(message);
        setBackground(theme.rounded(style.soft, 12));
        icon.setImageResource(style.icon);
        icon.setColorFilter(style.accent, PorterDuff.Mode.SRC_IN);
        text.setText(style.text);
        detail.setText(style.detail);
        detail.setVisibility(style.detail.isBlank() ? GONE : VISIBLE);
        boolean showAction = style.offerContinue || style.offerRetry;
        action.setVisibility(showAction ? VISIBLE : GONE);
        continueAction = style.offerContinue;
        if (showAction) {
            action.setText(style.offerContinue ? R.string.ai_continue : R.string.ai_retry);
            action.setTextColor(style.accent == theme.text3 ? theme.brand : style.accent);
            action.setBackground(theme.pressable(theme.rounded(theme.card, 14), 14));
        }
    }

    @NonNull
    private NoticeStyle styleFor(@NonNull AiMessageUi message) {
        return switch (message.notice()) {
            case STOPPED -> new NoticeStyle(theme.fill, theme.text3, R.drawable.ic_ai_stop,
                    theme.context.getString(R.string.ai_problem_stopped), "", false, false);
            case TRUNCATED -> new NoticeStyle(theme.warningSoft, theme.warning,
                    R.drawable.ic_ai_warning,
                    theme.context.getString(R.string.ai_problem_truncated), "",
                    message.canContinue(), false);
            case FILTERED -> new NoticeStyle(theme.warningSoft, theme.warning,
                    R.drawable.ic_ai_shield,
                    theme.context.getString(R.string.ai_problem_filtered), "", false, false);
            case EMPTY -> new NoticeStyle(theme.fill, theme.text3, R.drawable.ic_ai_warning,
                    theme.context.getString(R.string.ai_problem_empty), "",
                    false, message.canRegenerate());
            default -> new NoticeStyle(theme.alertSoft, theme.alert, R.drawable.ic_ai_warning,
                    message.failureKind() == null
                            ? theme.context.getString(R.string.ai_failure_unknown)
                            : theme.context.getString(AiMessages.failure(message.failureKind())),
                    message.failureDetail(), false, message.canRegenerate());
        };
    }

    private record NoticeStyle(@ColorInt int soft, @ColorInt int accent,
                               @DrawableRes int icon, @NonNull String text,
                               @NonNull String detail, boolean offerContinue,
                               boolean offerRetry) {
    }
}
