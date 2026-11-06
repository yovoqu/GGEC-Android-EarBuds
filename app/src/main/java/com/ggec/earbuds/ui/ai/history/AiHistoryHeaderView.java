package com.ggec.earbuds.ui.ai.history;

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

/** 历史面板页头：页名、新建和关闭。统一采用 {@link AiHeaderActionButton} 圆形拟物微质感按键。 */
final class AiHistoryHeaderView {

    private final LinearLayout view;

    AiHistoryHeaderView(@NonNull AppCompatActivity activity, @NonNull AiTheme theme,
                        @NonNull Runnable onNew, @NonNull Runnable onClose) {
        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinimumHeight(theme.dp(AiTheme.HEADER_HEIGHT));
        view.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);

        TextView title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(R.string.ai_history_panel_title);
        view.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        AiHeaderActionButton newChat = new AiHeaderActionButton(activity, theme, AiHeaderActionButton.Style.NEUTRAL)
                .icon(R.drawable.ic_ai_edit)
                .contentDesc(R.string.ai_history_new_chat)
                .onClick(ignored -> onNew.run());
        LinearLayout.LayoutParams newChatParams = AiHeaderActionButton.createLinearLayoutParams(theme);
        newChatParams.setMarginEnd(theme.dp(AiTheme.SPACE_S));
        view.addView(newChat, newChatParams);

        AiHeaderActionButton close = new AiHeaderActionButton(activity, theme, AiHeaderActionButton.Style.NEUTRAL)
                .icon(R.drawable.ic_ai_close)
                .contentDesc(R.string.ai_history_close)
                .onClick(ignored -> onClose.run());
        view.addView(close, AiHeaderActionButton.createLinearLayoutParams(theme));
    }

    @NonNull
    View view() {
        return view;
    }
}
