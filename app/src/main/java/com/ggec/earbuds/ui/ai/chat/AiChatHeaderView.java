package com.ggec.earbuds.ui.ai.chat;

import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/** 对话页页头：历史、页名、通话与更多。只负责展示和转发点击。 */
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
        header.setPadding(theme.dp(AiTheme.SPACE_XS), theme.dp(AiTheme.SPACE_XS),
                theme.dp(10), 0);

        ImageView history = icon(activity, theme, R.drawable.ic_ai_history, theme.text2,
                R.string.ai_history_entry, listener::onHistory);
        header.addView(history, iconParams(theme));

        TextView title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(R.string.ai_title);
        LayoutParams titleParams = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(AiTheme.SPACE_XS));
        header.addView(title, titleParams);

        ImageView call = icon(activity, theme, R.drawable.ic_ai_call_entry, theme.brand,
                R.string.ai_call_entry, listener::onCall);
        header.addView(call, iconParams(theme));

        ImageView more = icon(activity, theme, R.drawable.ic_ai_more, theme.text2,
                R.string.ai_more_entry, listener::onMore);
        header.addView(more, iconParams(theme));
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

    @NonNull
    private static ImageView icon(@NonNull AppCompatActivity activity, @NonNull AiTheme theme,
                                  int resource, int tint, int description, @NonNull Runnable action) {
        ImageView icon = new ImageView(activity);
        icon.setImageResource(resource);
        icon.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = theme.dp(10);
        icon.setPadding(padding, padding, padding, padding);
        icon.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        icon.setContentDescription(activity.getString(description));
        icon.setOnClickListener(view -> action.run());
        return icon;
    }

    @NonNull
    private static LayoutParams iconParams(@NonNull AiTheme theme) {
        return new LayoutParams(theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET));
    }
}
