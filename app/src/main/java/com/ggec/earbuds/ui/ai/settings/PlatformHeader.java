package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;

/** 平台详情页页头：返回、标题与可选徽标。统一采用 {@link AiHeaderActionButton} 圆形拟物微质感按键。 */
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
        root.setMinimumHeight(theme.dp(AiTheme.HEADER_HEIGHT));
        root.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);

        AiHeaderActionButton back = new AiHeaderActionButton(context, theme, AiHeaderActionButton.Style.NEUTRAL)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.ai_cancel)
                .onClick(view -> onBack.run());
        root.addView(back, AiHeaderActionButton.createLinearLayoutParams(theme));

        title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(AiTheme.SPACE_M));
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

    void bind(@NonNull String nextTitle, @Nullable AiText nextBadge) {
        String resolved = nextBadge == null ? null : nextBadge.resolve(context);
        String nextSignature = nextTitle + "\u0000" + resolved;
        if (nextSignature.equals(signature)) {
            return;
        }
        signature = nextSignature;
        title.setText(nextTitle);
        badge.setVisibility(resolved == null ? View.GONE : View.VISIBLE);
        if (resolved != null) {
            badge.setText(resolved);
        }
    }
}
