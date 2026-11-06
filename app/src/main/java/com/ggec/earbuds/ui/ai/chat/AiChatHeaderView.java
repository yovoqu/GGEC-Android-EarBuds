package com.ggec.earbuds.ui.ai.chat;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;

/** 对话页页头：历史、页名、通话与更多。统一采用 {@link AiHeaderActionButton} 圆形拟物微质感按键。 */
final class AiChatHeaderView extends LinearLayout {

    interface Listener {
        void onHistory();
        void onCall();
        void onMore();
    }

    private final View moreAnchor;
    private final View rule;

    AiChatHeaderView(@NonNull AppCompatActivity activity, @NonNull AiTheme theme,
                     @NonNull Listener listener) {
        super(activity);
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(theme.dp(AiTheme.HEADER_HEIGHT));
        header.setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_XS),
                theme.dp(AiTheme.PAGE_INSET), 0);

        AiHeaderActionButton history = new AiHeaderActionButton(activity, theme, AiHeaderActionButton.Style.NEUTRAL)
                .icon(R.drawable.ic_ai_history)
                .contentDesc(R.string.ai_history_entry)
                .onClick(v -> listener.onHistory());
        header.addView(history, AiHeaderActionButton.createLinearLayoutParams(theme));

        TextView title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(R.string.ai_title);
        LayoutParams titleParams = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(AiTheme.SPACE_M));
        header.addView(title, titleParams);

        AiHeaderActionButton call = new AiHeaderActionButton(activity, theme, AiHeaderActionButton.Style.BRAND)
                .icon(R.drawable.ic_ai_call_entry)
                .contentDesc(R.string.ai_call_entry)
                .onClick(v -> listener.onCall());
        LinearLayout.LayoutParams callParams = AiHeaderActionButton.createLinearLayoutParams(theme);
        callParams.setMarginEnd(theme.dp(AiTheme.SPACE_S));
        header.addView(call, callParams);

        AiHeaderActionButton more = new AiHeaderActionButton(activity, theme, AiHeaderActionButton.Style.NEUTRAL)
                .icon(R.drawable.ic_ai_more)
                .contentDesc(R.string.ai_more_entry)
                .onClick(v -> listener.onMore());
        header.addView(more, AiHeaderActionButton.createLinearLayoutParams(theme));
        moreAnchor = more;
        addView(header, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rule = new View(activity);
        rule.setBackgroundColor(theme.divider);
        rule.setAlpha(0f);
        addView(rule, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1))));
    }

    @NonNull
    View moreAnchor() {
        return moreAnchor;
    }

    void setRuleVisible(boolean visible) {
        float target = visible ? 1f : 0f;
        if (rule.getAlpha() != target) {
            rule.animate().cancel();
            rule.animate().alpha(target).setDuration(160).start();
        }
    }
}
