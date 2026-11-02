package com.ggec.earbuds.ui.ai.chat;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/** 助手回复的思考过程：标题行负责展开动作，正文只在展开时更新。 */
final class AiReasoningView extends LinearLayout {

    private final AiTheme theme;
    private final AiMessageActions actions;
    private final TextView title;
    private final ImageView chevron;
    private final TextView body;
    private String shown = "";
    @Nullable
    private String messageId;

    AiReasoningView(@NonNull AiTheme theme, @NonNull AiMessageActions actions) {
        super(theme.context);
        this.theme = theme;
        this.actions = actions;
        setOrientation(VERTICAL);
        setPadding(theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_S));

        LinearLayout header = new LinearLayout(theme.context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOnClickListener(view -> {
            if (messageId != null) {
                actions.onToggleReasoning(messageId);
            }
        });
        title = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2, AiTheme.Weight.MEDIUM);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        chevron = theme.icon(R.drawable.ic_ai_chevron_down, theme.text3);
        header.addView(chevron, new LinearLayout.LayoutParams(
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_L)));
        addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        body = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        body.setLineSpacing(theme.dpf(3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = theme.dp(8);
        addView(body, bodyParams);
    }

    void bind(@NonNull AiMessageUi message) {
        messageId = message.id();
        String reasoning = message.reasoning();
        if (reasoning == null) {
            setVisibility(GONE);
            shown = "";
            return;
        }
        setVisibility(VISIBLE);
        boolean thinking = message.streaming() && message.text().isEmpty();
        boolean expanded = message.reasoningExpanded();
        title.setText(thinking ? R.string.ai_reasoning_thinking : R.string.ai_reasoning_title);
        body.setVisibility(expanded ? VISIBLE : GONE);
        chevron.setRotation(expanded ? 180f : 0f);
        setBackground(expanded ? theme.rounded(theme.fill, AiTheme.RADIUS_ROW) : null);
        int insetX = expanded ? theme.dp(AiTheme.SPACE_M) : 0;
        int insetY = expanded ? theme.dp(AiTheme.SPACE_S) : theme.dp(AiTheme.SPACE_XS);
        if (getPaddingLeft() != insetX || getPaddingTop() != insetY) {
            setPadding(insetX, insetY, insetX, insetY);
        }
        if (expanded && !reasoning.equals(shown)) {
            body.setText(reasoning.strip());
            shown = reasoning;
        }
    }
}
