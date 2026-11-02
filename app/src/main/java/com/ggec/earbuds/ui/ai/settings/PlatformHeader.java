package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
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

/** 平台详情页页头：返回、标题与可选徽标。只负责展示。 */
final class PlatformHeader {

    private final Context context;
    private final LinearLayout root;
    private final TextView title;
    private final TextView badge;
    @Nullable
    private String signature;

    PlatformHeader(@NonNull Context context, @NonNull AiTheme theme, @NonNull Runnable onBack) {
        this.context = context;
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);

        ImageView back = theme.icon(R.drawable.ic_ai_settings_back, theme.text1);
        back.setContentDescription(context.getString(R.string.ai_cancel));
        back.setPadding(theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_S));
        back.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        back.setOnClickListener(view -> onBack.run());
        root.addView(back, new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));

        title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = theme.dp(AiTheme.SPACE_S);
        root.addView(title, titleParams);

        badge = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        badge.setPadding(theme.dp(AiTheme.SPACE_S), 0, theme.dp(AiTheme.SPACE_S), 0);
        badge.setBackground(theme.rounded(theme.fill, AiTheme.RADIUS_ROW));
        badge.setVisibility(View.GONE);
        root.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @NonNull
    View view() {
        return root;
    }

    void bind(@NonNull String nextTitle, @Nullable String nextBadge) {
        String nextSignature = nextTitle + "\u0000" + nextBadge;
        if (nextSignature.equals(signature)) {
            return;
        }
        signature = nextSignature;
        title.setText(nextTitle);
        badge.setVisibility(nextBadge == null ? View.GONE : View.VISIBLE);
        if (nextBadge != null) {
            badge.setText(nextBadge);
        }
    }
}
