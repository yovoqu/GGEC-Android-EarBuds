package com.ggec.earbuds.ui.ai.history;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/** 历史面板页头：页名、新建和关闭。 */
final class AiHistoryHeaderView {

    private final AppCompatActivity activity;
    private final AiTheme theme;
    private final LinearLayout view;

    AiHistoryHeaderView(@NonNull AppCompatActivity activity, @NonNull AiTheme theme,
                        @NonNull Runnable onNew, @NonNull Runnable onClose) {
        this.activity = activity;
        this.theme = theme;
        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(10), 0);

        TextView title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(R.string.ai_history_panel_title);
        view.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        view.addView(iconButton(R.drawable.ic_ai_edit, theme.text2,
                R.string.ai_history_new_chat, ignored -> onNew.run()));
        view.addView(iconButton(R.drawable.ic_ai_call_close, theme.text2,
                R.string.ai_history_close, ignored -> onClose.run()));
    }

    @NonNull
    View view() {
        return view;
    }

    @NonNull
    private ImageView iconButton(@DrawableRes int icon, int tint, @StringRes int description,
                                 @NonNull View.OnClickListener onClick) {
        ImageView button = theme.icon(icon, tint);
        int pad = theme.dp(8);
        button.setPadding(pad, pad, pad, pad);
        button.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        button.setContentDescription(activity.getString(description));
        button.setOnClickListener(onClick);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));
        return button;
    }
}
