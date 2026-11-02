package com.ggec.earbuds.ui.ai.chat;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.common.AiFormat;

/** 助手回复的底部动作与统计：复制、重试、删除和耗时/速率/token。 */
final class AiAssistantFooterView extends LinearLayout {

    interface Listener {
        void onCopy(@NonNull String text);
        void onRegenerate();
        void onDelete(@NonNull String messageId);
    }

    private final AiTheme theme;
    private final Listener listener;
    private final ImageView copy;
    private final ImageView regenerate;
    private final ImageView delete;
    private final TextView meta;
    @Nullable
    private AiMessageUi message;

    AiAssistantFooterView(@NonNull AiTheme theme, @NonNull Listener listener) {
        super(theme.context);
        this.theme = theme;
        this.listener = listener;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        copy = button(R.drawable.ic_ai_copy, R.string.ai_copy);
        regenerate = button(R.drawable.ic_ai_refresh, R.string.ai_action_regenerate);
        delete = button(R.drawable.ic_ai_delete, R.string.ai_delete);
        addView(copy);
        addView(regenerate);
        View gap = new View(theme.context);
        addView(gap, new LinearLayout.LayoutParams(theme.dp(12), 1));
        addView(delete);

        meta = theme.text(12, theme.text3);
        meta.setGravity(Gravity.END);
        meta.setSingleLine(true);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metaParams.setMarginStart(theme.dp(8));
        addView(meta, metaParams);

        copy.setOnClickListener(view -> {
            if (message != null) {
                listener.onCopy(message.text());
            }
        });
        regenerate.setOnClickListener(view -> listener.onRegenerate());
        delete.setOnClickListener(view -> {
            if (message != null) {
                listener.onDelete(message.id());
            }
        });
    }

    void bind(@NonNull AiMessageUi next) {
        message = next;
        boolean showCopy = !next.text().isEmpty();
        boolean showRegenerate = next.canRegenerate()
                && next.notice() != AiMessageUi.Notice.ERROR
                && next.notice() != AiMessageUi.Notice.EMPTY;
        boolean showDelete = next.canDelete();
        setVisibility(!next.streaming() && (showCopy || showRegenerate || showDelete)
                ? VISIBLE : GONE);
        copy.setVisibility(showCopy ? VISIBLE : GONE);
        regenerate.setVisibility(showRegenerate ? VISIBLE : GONE);
        delete.setVisibility(showDelete ? VISIBLE : GONE);

        StringBuilder text = new StringBuilder();
        if (next.elapsedMillis() > 0) {
            text.append(AiFormat.duration(next.elapsedMillis()));
        }
        int tps = AiFormat.tokensPerSecond(next.outputTokens(), next.elapsedMillis());
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

    @NonNull
    private ImageView button(@DrawableRes int icon, @StringRes int description) {
        return theme.iconButton(icon, theme.text3, 40, 10,
                theme.context.getString(description));
    }
}
